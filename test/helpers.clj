(ns helpers
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import java.nio.file.Files
           java.nio.file.Paths
           java.nio.file.attribute.FileAttribute))

(defmacro defmodule [& code]
  (let [deps-map (->> code
                      (vec)
                      (keep (fn [[fst & rst]]
                              (when (= fst 'defmanifest)
                                (let [manifest (-> rst first vec)
                                      deps (some (fn [[k v]] (when (= k :deps) v))
                                                 manifest)]
                                  (when deps
                                    [(pr-str deps) deps])))))
                      (into {}))]
    `(let [temp-file# (Files/createTempFile (Paths/get "test/tmp_modules" (make-array String 0))
                                            "tmp_module_"
                                            ".clj"
                                            (make-array FileAttribute 0))
           temp-module-name# (-> temp-file#
                                 (.getFileName)
                                 (str)
                                 (str/replace #".clj$" "")
                                 (str/replace "_" "-"))
           temp-module-ns-name# (str "tmp-modules." temp-module-name#)]
       (with-open [f# (io/writer (str temp-file#))]
         (binding [*out* f#]
           (let [ns-expr# (-> []
                              (conj (symbol "ns"))
                              (conj (symbol temp-module-ns-name#))
                              (conj `(:require [~(symbol "system")
                                                :refer
                                                ~[(symbol "defmanifest")
                                                  (symbol "defstart")
                                                  (symbol "defstop")]])))]
             (->> ns-expr#
                  (apply list)
                  (pr-str)
                  (println)))

           (let [deps-map# ~deps-map]
             (doseq [[fst# & rst# :as expr#] (vec '~code)]
               (if (= fst# (symbol "defmanifest"))
                 (let [manifest# (first rst#)
                       deps-key# (some-> manifest# :deps pr-str)
                       manifest# (cond-> manifest#
                                   deps-key#
                                   (assoc :deps
                                          (get deps-map# deps-key#)))
                       defmanifest-expr# (-> []
                                             (conj (symbol "defmanifest"))
                                             (conj manifest#))]
                   (->> defmanifest-expr#
                        (apply list)
                        (pr-str)
                        (println)))
                 (-> expr# pr-str println))))))
       (keyword temp-module-ns-name#))))

(comment

  (let [module-a (defmodule
                   (def a ::a)
                   (defmanifest {:config {:a a}})
                   (defmanifest {:config {:a :test-1.name/help}})
                   (defstart [ctx cfg] (println "Hello World")))
        module-b (defmodule
                   (def a ::a)
                   (defmanifest {:config {:a a}})
                   (defmanifest {:config {:a :test-1.name/help} :deps [module-a]}))]
    module-b)

  (defn ensure-system-test-defined []
    (defmodule

      (require '[system])
      (require '[clojure.spec.alpha :as s])

      (def ns-helper nil)
      (def module-ns (-> #'ns-helper meta :ns str))

      (def resource-type-kw (keyword module-ns "resourceType"))
      (def resource-map-kw  (keyword module-ns "resource-map"))

      (s/def resource-type-kw string?)
      (s/def resource-map-kw (s/keys :req-un [resource-type-kw]))

      (def message-kw           (keyword module-ns "message"))
      (def message-map-kw       (keyword module-ns "message-map"))

      (def error-message-kw     (keyword module-ns "error-message"))
      (def error-message-map-kw (keyword module-ns "error-message-map"))
      (def validation-errors-kw (keyword module-ns "validation-errors"))

      (s/def error-message-kw string?)
      (s/def error-message-map-kw (s/keys :req-un [message-kw]))
      (s/def validation-errors-kw (s/coll-of message-map-kw :kind vector?))

      (def system-test-stop? (atom nil))

      (defmanifest
        {:config {:param {:required true :type "string"}}
         :define-hook {(keyword module-ns "validate")   {:args [resource-map-kw] :result validation-errors-kw}
                       (keyword module-ns "middleware") {:args [(keyword module-ns "request")]}}
         :define-slot {(keyword module-ns "save")       {:args [resource-map-kw] :result resource-map-kw}}
         :events {:define {}
                  :subscribe {}}})

      (defstart
        [context config]
        (system/info context (keyword module-ns "start") config)
        (reset! system-test-stop? false)
        {:state :v1 :config config})

      (defstop
        [context state]
        (reset! system-test-stop? true)
        (system/info context (keyword module-ns "stop")))))

  (let [test-module  (ensure-system-test-defined)
        test-context (system/start-system
                      {:services [test-module]
                       test-module {:param "param"}})]
    (println
     "system-test-stop?"
     (-> test-module
         (name)
         (str "/system-test-stop?")
         (symbol)
         (find-var)
         (var-get)
         (deref)))

    (system/stop-system test-context)

    (println
     "system-test-stop?"
     (-> test-module
         (name)
         (str "/system-test-stop?")
         (symbol)
         (find-var)
         (var-get)
         (deref))))




  (def context (system/start-system
                {:services ["system-test"]
                 :system-test {:param "param"}}))


  (def a 5)
  (meta #'a)

  :rcf)
