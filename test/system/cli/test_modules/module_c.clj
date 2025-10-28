(ns system.cli.test-modules.module-c
  (:require
   [system :refer [defmanifest defstart defstop]]))

(defmanifest {:description "module-c"
              :config {:param-1 {:type "string" :required true}
                       :param-2 {:type "string" :required true}}})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstart
  [_ctx _config])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstop
  [_ctx _state])
