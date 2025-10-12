(ns system.cli
  (:require
   [clojure.string :as str]
   [system.config :refer [coercers type-validators]]
   [system.manifest :refer [find-manifests]]))

(defn long-opt-name
  [module param]
  (let [module-str (if (keyword? module) (name module) (str module))
        param-str (if (keyword? param) (name param) (str param))]
    (str module-str "." param-str)))

(defn long-opt
  [module param]
  (let [opt-name (long-opt-name module param)
        argument (-> param
                     (str/replace #"[-.]" "_")
                     (str/upper-case))]
    (format "--%s %s"
            opt-name
            argument)))

(defn opt-description
  [field-config]
  (prn-str
   (select-keys field-config
                [:type :default :required :sensitive])))

(defn opt-validator
  ([type]
   (let [validator (get type-validators type (constantly true))]
     (opt-validator type validator)))

  ([type validator]
   (let [coercer (get coercers type identity)]
     (fn [arg]
       (-> arg coercer validator)))))

(defn opt-properties
  [module param {:keys [type default required sensitive validator] :as _field-config}]
  (let [validators (cond-> []
                     (not= type "string[]")
                     (conj (opt-validator type)
                           (str "Expected type: " type))

                     (some? validator)
                     (conj (opt-validator type validator)
                           (str "Custom validator: " validator)))]
    (cond-> []
      (= type "string[]")
      (conj :update-fn (fnil conj [])
            :multi true)

      (not= type "string[]")
      (conj :parse-fn (get coercers type identity))

      (seq validators)
      (conj :validate validators)

      (some? default)
      (conj :default default)

      (some? required)
      (conj :missing
            (str "Missing argument: "
                 "--"
                 (long-opt-name module param)))

      ;; TODO: probably should remove this
      (some? sensitive)
      (identity))))

(defn manifest->cli-opts
  [module manifest]
  (vec
   (for [[param field-config] (:config manifest)]
     (-> [nil]
         (conj (long-opt module param))
         (conj (opt-description field-config))
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
  (let [all-module-names (map #(-> % first name) manifests)
        description (->> all-module-names
                         (map #(str \" % \"))
                         (str/join ", ")
                         (str "Available modules: "))]
    ["-m" "--modules MODULE" description
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
        (into (cli-opts-configs manifests))
        (into cli-opts-default))))

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn options->system-config [options]
  (let [all-module-names
        (->> (find-manifests)
             (map #(-> % first name)))

        module-param-options
        (filter (fn [[arg-key _arg-val :as _option]]
                  (some (fn [module-name]
                          (str/starts-with? (name arg-key)
                                            (str module-name ".")))
                        all-module-names))
                options)]
    (reduce (fn [acc [arg-key arg-val :as _option]]
              (let [[_ module-name param-name]
                    (re-matches #"(.*)\.([^.]+)$"
                                (name arg-key))]
                (assoc-in acc
                          [(keyword module-name)
                           (keyword param-name)]
                          arg-val)))
            {:services (:modules options)}
            module-param-options)))
