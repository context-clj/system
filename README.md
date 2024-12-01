# Context System

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

