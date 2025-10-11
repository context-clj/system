(ns system.cli
  (:require
   [clojure.string :as str]
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
  (throw
   (UnsupportedOperationException. "Not implemented")))
