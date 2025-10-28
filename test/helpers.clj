(ns helpers
  (:require
   [system.manifest :refer [find-manifests unload-module]]))

(defn- unload-all-modules []
  (doseq [[_ manifest] (find-manifests)]
    (unload-module manifest)))

(defn unload-all-modules-fixture [f]
  (unload-all-modules)
  (f))
