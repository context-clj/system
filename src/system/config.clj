(ns system.config
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]))

(s/def ::type #{"string" "string[]" "integer" "number" "keyword" "boolean"})
(s/def ::default any?)
(s/def ::required boolean?)
(s/def ::sensitive boolean?)
(s/def ::validator ifn?)

(s/def ::field-config
  (s/keys :req-un [::type]
          :opt-un [::default ::required ::sensitive ::validator]))

(s/def ::config-spec
  (s/map-of keyword? ::field-config))

(defn parse-int [s]
  (if (and (string? s) (re-matches #"^[-+]?[0-9]+$" s))
    (Integer/parseInt s)
    s))

(comment
  (parse-int "44")
  (parse-int "44.4")
  (parse-int "a")
  (parse-int "-1")
  )

(defn coerce-vector-of-strings [v]
  (if-not (string? v)
    v
    (->> (str/split v #",")
         (mapv str/trim)
         (remove str/blank?))))

(defn coerce-boolean [v]
  (cond (boolean? v) v
        (= "true" v) true
        (= "false" v) false
        :else v))

(def coercers
  {"integer" parse-int
   "keyword" keyword
   "boolean" coerce-boolean
   "string[]"  coerce-vector-of-strings})

(defn vector-of-strings? [v]
  (and (vector? v) (every? string? v)))

(def type-validators
  {"string" string?
   "string[]" vector-of-strings?
   "number" number?
   "boolean" boolean?
   "integer" int?})

(defn coerce-value [k v tp]
  (if-let [coercer (get coercers tp)]
    (coercer v)
    v))

(defn coerce [schema config]
  (let [defaults (->> schema (reduce (fn [acc [k {d :default}]] (if d (assoc acc k d) acc)) {}))
        config' (merge defaults config)]
    (->> config'
         (reduce (fn [config [k v]]
                   (->> (if-let [sch (get schema k)] (coerce-value k v (:type sch)) v)
                        (assoc config k))
                   ) {}))))

(defn validate-required [errors schema config]
  (->> schema
       (reduce (fn [acc [k {req :required}]]
                 (if req (conj acc k) acc)) [])
       (reduce (fn [acc k]
                 (if-not (contains? config k)
                   (conj acc {:message (str (name k) " is required")})
                   acc)) errors)))


(defn validate-type [errors type k v]
  (if-let [vld (get type-validators type)]
    (if-not (vld v)
      (conj errors {:message (str (name k) " - expected " v " of type " type " got " (clojure.core/type v))})
      errors)
    (conj errors {:message (str (name k) " - unknown type " type ". Should be one of " (str/join ", " (keys type-validators)))})))

(defn validate-validator [errors vld k v]
  (if-not (vld v)
    (conj errors {:message (str (name k) " - expected " v " pass " vld)})
    errors))

(defn validate-param [errors schema k v]
  (if-not schema
    (conj errors {:message (str (name k) "- unknown parameter")})
    (-> (validate-type errors (or (:type schema) "string") k v)
        (cond-> (:validator schema)
          (validate-validator (:validator schema) k v)))))

(defn validate-params [errors schema config]
  (->> config
       (reduce (fn [errors [k v]]
                 (validate-param errors (get schema k) k v))
               errors)))

(defn validate [schema config]
  (-> []
       (validate-required schema config)
       (validate-params schema config)))

(comment

  (def sch  {:port      {:type "integer" :default 5432 :validator #'pos-int?}
             :host      {:type "string" :required true}
             :database  {:type "string" :required true}
             :enable    {:type "boolean"}
             :password  {:type "string" :sensitive true :required true}
             :pool-size {:type "integer" :default 5 :validator #'pos-int? :required 1}
             :timeout   {:type "integer" :validator #'pos-int?}})


  (s/valid? ::config-spec sch)
  (s/explain-data ::config-spec sch)

  (coerce sch {:port "5432" :pool-size "-1" :host 4 :timeout 10 :enable "true"})
  (coerce sch {:port "5432" :pool-size "-1" :host 4 :timeout 10 :enable true})


  (validate sch {:port "5432" :pool-size -1 :host 4 :timeout 10 :boolean 1})

  (validate sch {:port 5432 :pool-size 10
                 :host "localhost"
                 :password "pwd"
                 :timeout 1000 :database "db"})

  )

