(ns system.cli
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [system.manifest :refer [find-manifests]]))

(defn long-opt
  [module param]
  (let [module (if (keyword? module) (name module) (str module))
        param (if (keyword? param) (name param) (str param))
        argument (-> param
                     (str/replace #"[-.]" "_")
                     (str/upper-case))]
    (format "--%s.%s %s"
            module
            param
            argument)))

(defn manifest->cli-opts
  [module manifest]
  (mapv (fn [[param _v]]
          [nil (long-opt module param) nil])
        (:config manifest)))

(defn cli-opts-configs
  [manifests]
  (->> manifests
       (filter #(-> % second :config))
       (map (fn [[module manifest]] (manifest->cli-opts module manifest)))
       (apply concat)
       (into [])))

(defn cli-opts-modules
  [manifests]
  (let [description (->> manifests
                         (map #(-> % first name))
                         (str/join ", ")
                         (str "Available modules: "))]
    ["-m" "--modules MODULE" description
     :multi true
     :update-fn (fnil conj [])]))

(def cli-opts-default
  [["-h" "--help" "Display help"]])

(defn parse-args
  [& args]
  (let [manifests (find-manifests)
        cli-opts (-> []
                     (conj (cli-opts-modules manifests))
                     (into (cli-opts-configs manifests))
                     (into cli-opts-default))]
    (println
     (parse-opts args cli-opts))))
