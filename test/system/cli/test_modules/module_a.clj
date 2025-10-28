(ns system.cli.test-modules.module-a
  (:require
   [system :refer [defmanifest defstart defstop]]))

(defmanifest {:description "module-a"
              :deps [:system.cli.test-modules.module-b
                     :system.cli.test-modules.module-c]
              :config {:param-1 {:type "string"}
                       :param-2 {:type "string[]"}
                       :param-3 {:type "integer"}
                       :param-4 {:type "number"}
                       :param-5 {:type "keyword"}
                       :param-6 {:type "boolean"}
                       :param-7 {:type "map"}
                       :param-8 {:type "string" :default "0xDEADBEEF"}}})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstart
  [_ctx _config])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstop
  [_ctx _state])


(def hello "Hello")
