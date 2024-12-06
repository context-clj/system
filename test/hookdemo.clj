(ns hookdemo
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [clojure.repl]))

(defn get-hooks [context hook-name])

(defmacro defhook [hook-name metas params & body]
  `(defn ~(with-meta hook-name (merge  metas {:system/type :hook-definition}))
     ~(into [] (butlast params))
     (let [~(last params) (get-hooks ~(first params) ~hook-name)]
       ~@body)))

(defmacro defhandler [handler-name hook-var metas params & body])

(defhook authorize
  {:desc "authorize hoook"}
  [context params hooks]
  (->> hooks
       (reduce (fn [acc hook]
                 ) {})))

(defhandler authorize #'authorize
  {:desc ""}
  [context params]
  (if (:ok params)
    true false))

(defn collect-hooks [-ns]
  (->> (ns-publics -ns)
       (filter (fn [[k v]] (= :hook-definition (:system/type (meta v)))))
       (mapv #(meta (second %)))))

(comment

  (collect-hooks *ns*)

  (meta #'authorize)


  (s/fdef my-func
    :args (s/cat :x int? :y int?)
    :ret int?)

  (defn my-func [x y]
    (+ x y))

  (my-func "a" "b")

  (st/instrument `my-func)

  (type `my-func)
  (meta #'my-func)

  (st/unstrument 'my-func)
  )
