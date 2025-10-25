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

context-clj allows you to configure and start your app using CLI.
You can either use a provided default runner or completely customize it for your own needs.

### How to run your app with a default runner

```shell
clj -M -i src/my_app_core.clj -m system \
    --modules '["<module-1>" "<module-2>"]' \
    --module-1.param-1 <param-1> \
    --module-2.param-2 <param-1>
```

`-i <filepath>` is used to provide your app's file that has a `defmanifest` for a main module (usually it is `core.clj` that has some sort of an entry point).

### How does CLI discover your app's modules

1. It starts with `clj -M -i src/my_app_core.clj` part of the command. It loads your "main" module.
2. If "main" module's manifest has `:deps`, then system loads their manifest too, including there deps. This is a recursive process.
3. Modules that can were discovered during this recursive loading process are called "reachable". CLI acknowledges their existence and display info on them.
4. Modules that were not discovered are call "unreachable" and you cannot interact with them via CLI.
   You can make them reachable if you insert them into the module discovering process:
    1. Make it a starting module `-i src/my_another_module.clj`
    2. Add them as a dependency to one of your "reachable" modules

### Help command

There is a nice `--help` flag that shows what modules your app has and prints info on module params:

```shell
clj -M -i src/my_app_core.clj -m system --help

# Default context-clj system runner
#
# Usage:
#   clj -M -i <path-to-main-module.clj> -m system
#       --modules '["<module-1>" "<module-2>"]'
#       --module-1.param-1 <param-1>
#       --module-2.param-2 <param-2>
#
# Options:
#       --modules MODULE                   Available modules: "core", "module-a"
#       --core.param-1 PARAM_1             {:spec {:type "string[]", :required true}, :env {:env-var "CORE__PARAM_1", :env-val nil}}
#       --core.param-2 PARAM_2      Hello  {:spec {:type "string", :default "Hello", :required true}, :env {:env-var "CORE__PARAM_2", :env-val nil}}
#       --core.param-3 PARAM_3             {:spec {:type "map", :required true}, :env {:env-var "CORE__PARAM_3", :env-val nil}}
#       --core.param-4 PARAM_4      1      {:spec {:type "string[]", :default 1}, :env {:env-var "CORE__PARAM_4", :env-val nil}}
#       --module-a.param-1 PARAM_1         {:spec {:type "integer"}, :env {:env-var "MODULE_A__PARAM_1", :env-val nil}}
#   -h, --help                             Display help
#
# Please refer to context-clj README to override the default runner:
# https://github.com/context-clj/system/blob/main/README.md
```

You can also display help only for selected modules by providing `--modules` additionally to `--help`:

```shell
clj -M -i src/my_app_core.clj -m system --modules module-a --help

# Default context-clj system runner
#
# Usage:
#   clj -M -i <path-to-main-module.clj> -m system
#       --modules '["<module-1>" "<module-2>"]'
#       --module-1.param-1 <param-1>
#       --module-2.param-2 <param-2>
#
# Options:
#       --modules MODULE            Available modules: "module-a"
#       --module-a.param-1 PARAM_1  {:spec {:type "integer"}, :env {:env-var "MODULE_A__PARAM_1", :env-val nil}}
#   -h, --help                      Display help
#
# Please refer to context-clj README to override the default runner:
# https://github.com/context-clj/system/blob/main/README.md
```

### Passing system config params of complex types

1. `string[]`

    ```shell
    # Option 1. Pass comma-separated values
    clj -M -i src/my_app_core.clj -m system --core.param-1 "1337, foobar, Hello World"

    # Option 2. Pass as an EDN vector. It is recommended to wrap the vector in single-quotes
    clj -M -i src/my_app_core.clj -m system --core.param-1 '["1337" "foobar" "Hello World"]'
    ```

2. `map`

    ```shell
    # Option 1. Pass as a JSON string
    clj -M -i src/my_app_core.clj -m system --core.param-3 '{"a": 42, "b": true, "c": "foobar"}'

    # Option 2. Pass as an EDN map. It is recommended to wrap the map in single-quotes
    clj -M -i src/my_app_core.clj -m system --core.param-1 '{:a 42, :b true, :c "foobar"}'
    ```

### Environment variables

CLI parser looks for module params in environment variables.

Environment variables should have a name formatted in a specific way: `MODULE__PARAM_NAME`

| Module   | Param     | Argument             | Environment         |
|----------|-----------|----------------------|---------------------|
| core     | param-1   | --core.param-1       | CORE__PARAM_1       |
| module-a | param-2   | --module-a.param-2   | MODULE_A__PARAM_2   |
| module-b | param-3-4 | --module-b.param-3-4 | MODULE_B__PARAM_3_4 |

CLI arguments have higher priority than environment variables,
meaning that environment variables can be overridden by command-line arguments.

You can always check what environment variable name corresponds to a command-line argument and see its current value using `--help` command.
All information is available in the argument description column under `:env` key.

```text
Options:
      --modules MODULE                   Available modules: "core", "module-a"
      --core.param-1 PARAM_1             {:spec {:type "string[]", :required true}, :env {:env-var "CORE__PARAM_1", :env-val ["World"]}}
      --core.param-2 PARAM_2      Hello  {:spec {:type "string", :default "Hello", :required true}, :env {:env-var "CORE__PARAM_2", :env-val nil}}
      --core.param-3 PARAM_3             {:spec {:type "map", :required true}, :env {:env-var "CORE__PARAM_3", :env-val nil}}
      --core.param-4 PARAM_4      1      {:spec {:type "string[]", :default 1}, :env {:env-var "CORE__PARAM_4", :env-val ["Hello" "World" "!"]}}
      --module-a.param-1 PARAM_1         {:spec {:type "integer"}, :env {:env-var "MODULE_A__PARAM_1", :env-val nil}}
```

### How to override default runner and create a custom CLI

Most of the time default runner should be sufficient, however if you feel the need to add custom logic to the
CLI parsing, we provide a bunch of simple functions as an API that allows you to change CLI behavior completely:

1. `system.cli/parse-args`
2. `system.cli/error-msg`
3. `system.cli/exit`
4. `system.cli/options->system-config`

You can take the default runner's code as a template to create a custom CLI in your app's code:

```clojure
(defn -main [& args]
  (let [{:keys [options errors summary]}
        (cli/parse-args args)]
    (cond
      (:help options)
      (cli/exit 0 (usage summary))

      errors
      (cli/exit 1 (cli/error-msg errors))

      :else
      (let [system-config (cli/options->system-config options)]
        (start-system system-config)))))
```

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
