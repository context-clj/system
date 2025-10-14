(ns system.cli
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [environ.core :refer [env]]
   [system.config :refer [coercers type-validators]]
   [system.manifest :refer [find-manifests]]))

(defn- ->str [v]
  (if (keyword? v)
    (name v)
    (str v)))

(defn long-opt-name
  [module param]
  (str (->str module)
       "."
       (->str param)))

(defn long-opt
  [module param]
  (let [opt-name (long-opt-name module param)
        argument (-> param
                     (name)
                     (str/replace #"[-.]" "_")
                     (str/upper-case))]
    (format "--%s %s"
            opt-name
            argument)))

(defn env-var
  [module param]
  (let [module-part
        (-> module
            (->str)
            (str/replace #"[-.]" "_"))

        param-part
        (-> param
            (->str)
            (str/replace #"[-.]" "_"))]
    (str/upper-case
     (str module-part "__" param-part))))

(defn env-key
  [module param]
  (let [module-part
        (-> module
            (->str)
            (str/replace #"\." "-"))

        param-part
        (-> param
            (->str)
            (str/replace #"\." "-"))]
    (keyword
     (str module-part "--" param-part))))

(defn env-val
  [param-type module param]
  (let [env-key' (env-key module param)
        env-val' (env env-key')
        coercer  (get coercers param-type identity)]
    (when (some? env-val')
      (if (= param-type "string[]")
        (try
          (-> env-val' read-string vec)
          (catch Exception _e
            (coercer env-val')))
        (coercer env-val')))))

(defn opt-description
  [module param field-config]
  (prn-str
   {:spec (select-keys field-config
                       [:type :default :required :sensitive])
    :env {:env-var (env-var module param)
          :env-val (env-val (:type field-config) module param)}}))

(defn opt-validator
  ([param-type]
   (let [validator (get type-validators param-type (constantly true))]
     (opt-validator param-type validator)))

  ([param-type validator]
   (let [coercer (get coercers param-type identity)]
     (fn [arg]
       (-> arg coercer validator)))))

(defn opt-properties
  [module param {param-type :type
                 default    :default
                 required   :required
                 validator  :validator
                 :as _field-config}]
  (let [validators (cond-> []
                     (not= param-type "string[]")
                     (conj (opt-validator param-type)
                           (str "Expected type: " param-type))

                     (some? validator)
                     (conj (opt-validator param-type validator)
                           (str "Custom validator: " validator)))]
    (cond-> []
      (= param-type "string[]")
      (conj :update-fn (fnil conj [])
            :multi true)

      (not= param-type "string[]")
      (conj :parse-fn (get coercers param-type identity))

      (seq validators)
      (conj :validate validators)

      (some? default)
      (conj :default default
            :default-fn (fn [_options]
                          (or (env-val param-type module param)
                              default)))

      (and (some? required)
           (nil? (env-val param-type module param)))
      (conj :missing
            (str "Missing argument: "
                 "--"
                 (long-opt-name module param))))))

(defn manifest->cli-opts
  [module manifest]
  (vec
   (for [[param field-config] (:config manifest)]
     (-> [nil]
         (conj (long-opt module param))
         (conj (opt-description module param field-config))
         (into (opt-properties module param field-config))))))

(defn cli-opts-configs
  [manifests]
  (->> manifests
       (filter #(-> % second :config))
       (map (fn [[module manifest]] (manifest->cli-opts module manifest)))
       (apply concat)
       (into [])))

(defn cli-opts-modules
  [manifests]
  (let [all-module-names
        (map #(-> % first name) manifests)

        description
        (->> all-module-names
             (map #(str \" % \"))
             (str/join ", ")
             (str "Available modules: "))]
    [nil "--modules MODULE" description
     :multi true
     :update-fn (fnil conj [])
     :missing "Must provide at least one module using --modules argument"
     :validate [(fn [arg]
                  (->> all-module-names
                       (filter #(= % arg))
                       (seq)))
                (fn [arg]
                  (str "No module named \"" arg "\" found. "
                       description))]]))

(def cli-opts-default
  [["-h" "--help" "Display help"]])

(defn cli-opts-from-manifests []
  (let [manifests (find-manifests)]
    (-> []
        (conj (cli-opts-modules manifests))
        (into (cli-opts-configs manifests)))))


;;;; ===============================================================================================
;;;; Public API functions
;;;; ===============================================================================================

(defn parse-args
  ([args]
   (parse-args args []))

  ([args cli-opts-custom]
   (let [cli-opts (-> []
                      (into (cli-opts-from-manifests))
                      (into cli-opts-custom)
                      (into cli-opts-default))]
     (parse-opts args cli-opts))))

(defn error-msg [errors]
  (str "The following errors occurred while parsing command line arguments:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn options->system-config [options]
  (let [manifests
        (find-manifests)

        all-module-param-long-opt-names
        (set
         (for [[module manifest]
               manifests

               param
               (-> manifest :config keys)]
           (long-opt-name module param)))

        env-vals
        (for [[module manifest]
              manifests

              [param field-config]
              (-> manifest :config)

              :let  [env-val' (env-val (:type field-config) module param)]
              :when (some? env-val')]
          {:module  module
           :param   param
           :env-val env-val'})

        module-param-options
        (filter (fn [[arg-key arg-val :as _option]]
                  (and (contains? all-module-param-long-opt-names
                                  (name arg-key))
                       (some? arg-val)))
                options)]
    (as-> {:services (:modules options)}
          system-config

      (reduce (fn [acc {:keys [env-val module param]}]
                (assoc-in acc
                          [(keyword module)
                           (keyword param)]
                          env-val))
              system-config
              env-vals)

      (reduce (fn [acc [arg-key arg-val :as _option]]
                (let [[_ module param]
                      (re-matches #"(.*)\.([^.]+)$"
                                  (name arg-key))]
                  (assoc-in acc
                            [(keyword module)
                             (keyword param)]
                            arg-val)))
              system-config
              module-param-options))))
