# Context System

Context framework is a way to build modular, composable and dynamic systems in clojure.

System consists of set of modules interacting
with each other by calling functions and hooks or pub/sub events.

Module is reusable library which represented as clojure namespace with `defmanifest`.
Statefull modules can declare `defstop/defstart` functions.
But module is not forced to be stateful.

System does:

* manages modules state with system/get-system-state and set-system-state
* manages configuration by validating params with manifest
* provides population params from cli and env variables
* allow dynamic change of configuration params - if it's allowed
* provides pluggable configuration storage
* provides uniform logging with system/info system/error

Basic module may look like:

```clj
(ns mymodule
  (:require [system]))

(defn helper-function [context params]
  (pg/load context ...))

(defn authorize [context request]
  (if-not (helper-function context params)
    (http/unauthorized {:message "..."})
    (http/authorized {})))

(defn service-function [context params]
 (let [result (pg/execute! context {:sql "select ..."})]
   (http/ok {:body (http/format context result)})))

;; define module with deps and configs, register-hooks
(system/defmanifest
  {:description "module description"
   :deps ["http" "http.openapi" "pg"]
   :register-hook {:http/authorize {:fn #'autorize}}
   :config {:api-key {:type "string" :required true}}})

(system/defstart
   [context config]
   (system/info context ::start)
   (http/register-endpoint context {:method :get :path "/service" :fn #'service-function})
   {:connection (connect-to-api config)})

(system/defstop
   [context state]
   (when-let [conn (:connection state)]
     (.stop conn)))

(comment
  (def system-cfg {:services ["mymodule"] :mymodule {:api-key "..."} :http {:port 8080}})
  (def context (system/start-system system-cfg))

  (service-function context {...})

  (http/request context {:path "/service"})

)

```

This is a core library for the whole context framework.

There are few building blocks:

* Module - module is a namespace with optional manifest,
  start and stop functions for stateful modules and a set of service functions
* Assembly - which is one clojure project with potentially multiple modules and context manifest
* System - is a set off assemblies with modules configured
* Runtime - is set of jars with assemblies and an entry point

modules could be versioned, but ideally they are not

```clj
;; context/http.manifest.edn
{:name "context/http"
 :description " .... "
 :modules ["http" "http.jwt-auth" "http.basic-auth"]}

```

Building platform:

You have a platform core and plugins,
which results in separate jars

fhir need a fhir.tx
there could be several implementations

* terbox
* box.tx

```clj
;; system fhir-server
{:modules ["logs.elastic"
           "pg"
           "http"
           "http.openapi"
           "http.basic-auth"
           "far"
           "fhir"
           "fhir.mpi"
           "fhir.tx" ;; "termbox"
           "smart-on-fhir"
           "iam.users"
           "gcp.buckets"
           "pubsub"
           "pubsub.kafka" ;; pubsub.gcp
           "ccda2fhir"
           "hl7v2"
           "forms"
           "mysystem"]}

- pg/port
- http/port
- pubsub.kafka/topic
- fhir.engine fhirschema
- fhir.storage-format fhir | aidbox
```

## Command-line interface

context-clj provides a powerful CLI for configuring and starting your application.
You can use the built-in default runner out of the box, or customize it to fit your specific needs.

### Quick start

Run your app using the default system runner:

```shell
clj -M -i src/my_app_core.clj -m system \
    --modules '["<module-1>" "<module-2>"]' \
    --module-1.param-1 <value-1> \
    --module-2.param-2 <value-2>
```

**Key options:**

* `-M` - Run Clojure with the main option
* `-i <filepath>` - Load your main module file (the one containing `defmanifest` for your entry point, typically `core.clj`)
* `-m system` - Invoke the system's main function
* `--modules` - Specify which modules to start
* `--<module>.<param>` - Configure module parameters

### Module discovery

The CLI uses a dependency-based discovery mechanism to determine which modules are available:

**Discovery process:**

1. **Initial load** - The CLI loads your main module specified with `-i src/my_app_core.clj`
2. **Dependency traversal** - If the main module's manifest contains `:deps`, the system recursively loads all dependencies and their transitive dependencies
3. **Reachable modules** - Any module discovered through this process is considered "reachable" and will be available for CLI interaction and configuration
4. **Unreachable modules** - Modules not found during discovery cannot be used via CLI

**Making modules reachable:**

If a module is unreachable, you have two options:

1. **Load it directly** - Add another `-i` option: `clj -M -i src/module_a.clj -i src/module_b.clj -m system`
2. **Add as dependency** - Include it in the `:deps` of an already reachable module's manifest

### Getting help

The `--help` flag displays all available modules and their configuration parameters:

```shell
clj -M -i src/my_app_core.clj -m system --help
```

**Example output:**

```text
Default context-clj system runner

Usage:
  clj -M -i <path-to-main-module.clj> -m system
      --modules '["<module-1>" "<module-2>"]'
      --module-1.param-1 <param-1>
      --module-2.param-2 <param-2>

Options:
      --modules MODULE                   Available modules: "core", "module-a"
      --core.param-1 PARAM_1             {:spec {:type "string[]", :required true}, :env {:env-var "CORE__PARAM_1", :env-val nil}}
      --core.param-2 PARAM_2      Hello  {:spec {:type "string", :default "Hello", :required true}, :env {:env-var "CORE__PARAM_2", :env-val nil}}
      --core.param-3 PARAM_3             {:spec {:type "map", :required true}, :env {:env-var "CORE__PARAM_3", :env-val nil}}
      --core.param-4 PARAM_4      1      {:spec {:type "string[]", :default 1}, :env {:env-var "CORE__PARAM_4", :env-val nil}}
      --module-a.param-1 PARAM_1         {:spec {:type "integer"}, :env {:env-var "MODULE_A__PARAM_1", :env-val nil}}
  -h, --help                             Display help
```

**Filtering help by module:**

Display help for specific modules only by combining `--modules` with `--help`:

```shell
clj -M -i src/my_app_core.clj -m system --modules '["module-a"]' --help
```

This shows only the parameters for `module-a`, making it easier to focus on specific module configuration.

### Configuring parameters with complex types

The CLI supports passing complex data types for module parameters:

#### String arrays (`string[]`)

You can pass string arrays in two ways:

```shell
# Option 1: Comma-separated values
clj -M -i src/my_app_core.clj -m system \
    --core.param-1 "value1, value2, value3"

# Option 2: EDN vector (recommended for values containing commas or special characters)
clj -M -i src/my_app_core.clj -m system \
    --core.param-1 '["value1" "value2" "value3"]'
```

#### Maps (`map`)

Maps can be passed as JSON or EDN:

```shell
# Option 1: JSON format
clj -M -i src/my_app_core.clj -m system \
    --core.config '{"host": "localhost", "port": 8080, "ssl": true}'

# Option 2: EDN format (recommended for Clojure data structures)
clj -M -i src/my_app_core.clj -m system \
    --core.config '{:host "localhost" :port 8080 :ssl true}'
```

> **Tip:** Always wrap EDN and JSON values in single quotes to prevent shell interpretation.

### Environment variables

The CLI automatically discovers and uses environment variables for module configuration, making it easier to configure your app in different environments (development, staging, production).

#### Naming convention

Environment variables follow this pattern: `MODULE__PARAM_NAME`

**Conversion rules:**

* Module names are uppercased: `core` → `CORE`
* Hyphens in parameter names become underscores: `param-name` → `PARAM_NAME`
* Module and parameter are separated by double underscores: `__`

**Examples:**

| Module     | Parameter   | CLI Argument               | Environment Variable      |
|------------|-------------|----------------------------|---------------------------|
| `core`     | `param-1`   | `--core.param-1`           | `CORE__PARAM_1`           |
| `module-a` | `param-2`   | `--module-a.param-2`       | `MODULE_A__PARAM_2`       |
| `module-b` | `param-3-4` | `--module-b.param-3-4`     | `MODULE_B__PARAM_3_4`     |
| `http`     | `port`      | `--http.port`              | `HTTP__PORT`              |
| `http`     | `ssl-cert`  | `--http.ssl-cert`          | `HTTP__SSL_CERT`          |

#### Parameter priority

When the same parameter is specified in multiple places, the CLI uses this priority order (highest to lowest):

1. **CLI arguments** - `--module.param value` (highest priority)
2. **Environment variables** - `MODULE__PARAM=value`
3. **Default values** - Specified in module manifest

This means CLI arguments will always override environment variables, which in turn override defaults.

#### Checking environment variable values

Use `--help` to see the environment variable name and current value for each parameter:

```shell
export CORE__PARAM_1='["Hello" "World"]'
clj -M -i src/my_app_core.clj -m system --help
```

```text
Options:
      --modules MODULE                   Available modules: "core", "module-a"
      --core.param-1 PARAM_1             {:spec {:type "string[]", :required true}, :env {:env-var "CORE__PARAM_1", :env-val ["Hello" "World"]}}
      --core.param-2 PARAM_2      Hello  {:spec {:type "string", :default "Hello", :required true}, :env {:env-var "CORE__PARAM_2", :env-val nil}}
      --module-a.param-1 PARAM_1         {:spec {:type "integer"}, :env {:env-var "MODULE_A__PARAM_1", :env-val nil}}
```

The `:env-val` field shows the current value from the environment variable, or `nil` if not set.

### Creating a custom CLI runner

While the default runner works for most use cases, you may want to customize CLI behavior for specific needs (e.g., custom validation, additional preprocessing, or integration with other tools).

#### Available API functions

context-clj provides these functions to build custom CLI runners:

| Function                             | Description                                                                 |
|--------------------------------------|-----------------------------------------------------------------------------|
| `system.cli/parse-args`              | Parses command-line arguments and returns options, errors, and help summary |
| `system.cli/error-msg`               | Formats error messages for display                                          |
| `system.cli/exit`                    | Exits the program with a status code and optional message                   |
| `system.cli/options->system-config`  | Converts parsed CLI options into a system configuration map                 |

#### Example: Custom runner

Here's a template for creating a custom CLI runner in your app:

```clojure
(ns my-app.core
  (:require [system.cli :as cli]
            [system]))

(defn usage [summary]
  (str "My Custom App Runner\n\n"
       "Usage: clj -M -i src/my_app.clj -m my-app.core [options]\n\n"
       "Options:\n" summary))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-args args)]
    (cond
      ;; Display help
      (:help options)
      (cli/exit 0 (usage summary))

      ;; Handle parsing errors
      errors
      (cli/exit 1 (cli/error-msg errors))

      ;; Custom validation example - validate port is in valid range
      (let [port (:http.port options)]
        (and port (or (< port 1024) (> port 65535))))
      (cli/exit 1 "Error: HTTP port must be between 1024 and 65535")

      ;; Start the system
      :else
      (let [system-config (cli/options->system-config options)]
        (println "Starting system with config:" system-config)
        (system/start-system system-config)))))
```

**Usage:**

```shell
# Use your custom runner instead of -m system
clj -M -i src/my_app.clj -m my-app.core --modules '["http"]' --http.port 8080
```

This approach gives you full control over CLI behavior while leveraging the built-in parsing and configuration logic.

## Logging

We have a very simplistic logging system at your disposal that you can easily incorporate into your app,
especially if you are already using this package.

### Logging functions

* `system/error`
* `system/info`
* `system/debug`

### Logging levels

| Name     | Level    | Note                                        |
|----------|:--------:|---------------------------------------------|
| `:off`   |    -1    | Special level to disable logging completely |
| `:error` |     0    |                                             |
| `:info`  |     1    | *Default logging level*                     |
| `:debug` |     2    |                                             |

Log functions print message only if their log level is lower or equal to context's log level.
For example, if context's log level is set to `:debug`, all logging functions will print a message.
However if log level set to `:info`, only `system/info` and `system/error` will display any message,
while `system/debug` won't produce any output.

### How to check context's current log level

```clj
(system/ctx-get-log-level ctx)
```

### How to declare global logging level in system's config

```clj
(def config {:system/log-level (system/log-levels :off)})

(def context (system/start-system config))
```

### How to set logging level dynamically

Logging level is always bound to the context object. You change logging level by creating
a new context with a specific logging level, that you can then pass to logging functions.

```clj
;; Create a new context with disabled logging
(let [ctx-without-logging (system/ctx-set-log-level ctx :off)]
  (system/error ctx-without-logging "None of these")
  (system/info  ctx-without-logging "messages will")
  (system/debug ctx-without-logging "be printed"))

;; Use original ctx
(system/error ctx "Printed as usual")
(system/info  ctx "All good here as well")
(system/debug ctx "Not printed because default log level is :info")
```

### Known issues

* Not thread-safe. Undefined behavior if you log in a multi-threaded application
* At the time being, no way to change output destination. All logging functions print to stdout using `println` under the hood

  (Open related issue: [Logging - file appender as module `context.logger.ndjson`](https://github.com/context-clj/system/issues/7))
