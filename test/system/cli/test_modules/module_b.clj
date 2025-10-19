(ns system.cli.test-modules.module-b
  (:require
   [system :refer [defmanifest defstart defstop]]))

(defmanifest {:description "module-b"})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstart
  [_ctx _config])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstop
  [_ctx _state])
