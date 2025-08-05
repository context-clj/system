(ns module-broken
  (:require [system]))

(system/defmanifest
  {:description "test module"})

(system/defstart
  [context config]
  (system/info context ::start config)
  (throw (ex-info "This module is broken!" {}))
  {:state :v1 :config config})

(system/defstop
  [context state]
  "This is never going to run")