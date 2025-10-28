(ns system.cli.test-modules.module-d
  (:require
   [system :refer [defmanifest defstart defstop]]))

(defmanifest {:description "module-d"})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstart
  [_ctx _config])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defstop
  [_ctx _state])
