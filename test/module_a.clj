(ns module-a
  (:require [system]))

(defn validate [_context resource]
  (if-not (:id resource)
    [{:message "id is required"}]
    []))

(defn save [_context resource]
  (assoc resource :ts "ts" :id "id"))

(system/defmanifest
  {:description "test module"
   :register-hook {:system-test/validate {::validate {:fn #'validate}}}
   :register-slot {:system-test/save {:fn #'save}}})
