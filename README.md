# Context System

Context framework is a way to build modular, composable and dynamic systems in clojure.

System consists of set of modules interacting
with each other by calling functions and hooks or pub/sub events.

Module is reusable library which represented as clojure namespace with `defmanifest`.
Statefull modules can declare `defstop/defstart` functions.
But module is not forced to be stateful.

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

