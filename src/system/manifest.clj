(ns system.manifest
  (:require
   [clojure.set :as set]
   [system.meta :refer [find-var-with-meta]]))

(defn find-manifest-var [ns]
  (find-var-with-meta ns :context-clj/manifest))

(defn find-manifests []
  (->> (all-ns)
       (map (fn [ns]
              (when-let [manifest-var (find-manifest-var ns)]
                {(-> ns ns-name keyword) @manifest-var})))
       (apply merge)))

(defn unload-module [manifest]
  (when-let [module-ns (-> manifest meta :ns)]
    (doseq [[s _] (ns-map module-ns)]
      (ns-unmap module-ns s))))

(defn load-deps [deps]
  (loop [all-deps #{}
         [dep & remain-deps] deps]
    (let [normalized-dep (some-> dep name)]
      (cond
        (not normalized-dep)
        all-deps

        (contains? all-deps normalized-dep)
        (recur all-deps remain-deps)

        :else
        (let [dep-ns-sym (symbol normalized-dep)]
          (when-not (some-> dep-ns-sym find-ns find-manifest-var)
            (require dep-ns-sym :reload))
          (let [manifest (-> dep-ns-sym find-ns find-manifest-var var-get)
                dep-deps (-> manifest :deps seq)]
            (recur (conj all-deps dep)
                   (cond-> remain-deps
                     dep-deps
                     (into (set/difference (set dep-deps)
                                           (set remain-deps)
                                           all-deps))))))))))

(defn unload-deps [deps]
  (loop [all-deps #{}
         [dep & remain-deps] deps]
    (let [normalized-dep (some-> dep name)]
      (cond
        (not normalized-dep)
        (do
          (doseq [dep all-deps
                  :let [dep-ns (-> dep symbol find-ns)
                        manifest-var (some-> dep-ns find-manifest-var)]
                  :when manifest-var]
            (unload-module @manifest-var))
          all-deps)

        :else
        (let [dep-ns-sym (symbol normalized-dep)
              manifest (some-> dep-ns-sym find-ns find-manifest-var var-get)
              dep-deps (:deps manifest)]
          (recur (conj all-deps dep)
                 (cond-> remain-deps
                   (seq dep-deps)
                   (into (set/difference (set dep-deps)
                                         (set remain-deps)
                                         all-deps)))))))))
