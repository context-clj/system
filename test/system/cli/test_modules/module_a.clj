(ns system.cli.test-modules.module-a
  (:require
   [system :refer [defmanifest defstart defstop]]))

(defmanifest {:description "module-a"
              :deps [:system.cli.test-modules.module-b
                     :system.cli.test-modules.module-c]})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstart
  [_ctx _config])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstop
  [_ctx _state])


(def hello "Hello")
