(ns system.meta)

(defn find-var-with-meta [ns meta-pred]
  (->> ns
       (ns-map)
       (vals)
       (filter var?)
       (some (fn [var]
               (when (some-> var var-get meta meta-pred)
                 var)))))
