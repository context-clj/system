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

(system/destart
   [context config]
   (system/info context ::start)
   (http/register-endpoint context {:method :get :path "/service" :fn #'service-function})
   {:connection (connect-to-api config)})

(system/destop
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

## How to run your app from a command line

```shell
clj -M -i src/my_app_core.clj -m system
```

`-i <filepath>` is used to provide your app's file that has a `defmanifest` for a main module (usually it is `core.clj` with `-main` function).

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
