(ns system-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcho.core :as matcho]
            [clojure.spec.alpha :as s]
            [system]
            [system.config :as config]
            [clojure.string :as str]))

(s/def ::resourceType string?)
(s/def ::resource-map (s/keys :req-un [::resourceType]))

(s/def ::error-message string?)
(s/def ::error-message-map (s/keys :req-un [::message]))
(s/def ::validation-errors (s/coll-of ::message-map :kind vector?))



(defn ensure-system-test-defined []
  (system/defmanifest
    {:config {:param {:required true :type "string"}}
     :define-hook {::validate   {:args [::resource-map] :result ::validation-errors}
                   ::middleware {:args [::request]}}
     :define-slot {::save       {:args [::resource-map] :result ::resource-map}}
     :events {:define {}
              :subscribe {}}}))


(comment
  (authorize context {})

  )

(def system-test-stop? (atom nil))

(system/defstart
  [context config]
  (system/info context ::start config)
  (reset! system-test-stop? false)
  {:state :v1 :config config})

(system/defstop
  [context state]
  (reset! system-test-stop? true)
  (system/info context ::stop))

(deftest basic-test
  (ensure-system-test-defined)
  (def context (system/start-system
                {:services ["system-test"]
                 :system-test {:param "param"}}))

  (matcho/match
   (system/get-system-state context [:config])
   {:param "param"})

  (testing "system state after start"
    (matcho/match (system/get-system-state context [:state]) :v1))

  (testing "set-system-state"
    (system/set-system-state context [:state] :v2)
    (matcho/match (system/get-system-state context [:state]) :v2))

  (system/clear-system-state context [:state])
  (matcho/match (system/get-system-state context [:state]) nil?)

  (is (thrown-with-msg?
       Exception #"Invalid config"
       (system/start-system {:services ["system-test"] :system-test {:param 1}})))

  (matcho/match @system-test-stop? false)
  (system/stop-system context)
  (is (= true @system-test-stop?))

  (def ctx-with-cache (system/new-context context))
  (def num-cache-calls (atom 0))

  (system/get-context-cache ctx-with-cache [:cached] (fn [] (swap! num-cache-calls inc) :ok))
  (system/get-context-cache ctx-with-cache [:cached] (fn [] (swap! num-cache-calls inc) :ok))
  (system/get-context-cache ctx-with-cache [:cached] (fn [] (swap! num-cache-calls inc) :ok))

  (matcho/match @num-cache-calls 1)

  )

(defn process-middlewares [context request]
  (let [context (system/reduce-hooks-into-context context ::middleware request)]
    context))

(defn set-user [context req]
  (system/ctx-set context [:user] {:id "admin"}))

(defn get-user [context]
  (system/ctx-get context [:user]))

(defn set-client [context req]
  (system/ctx-set context [:client] {:id "client"}))

(defn get-client [context]
  (system/ctx-get context [:client]))

(defn save [context resource]
  (system/call-slot context ::save resource))

(defn create [context resource]
  (let [errors (system/reduce-hooks-into-vector context ::validate resource)]
    (if (seq errors)
      {:status :error :errors errors}
      {:status :ok :resource resource})))


(deftest test-slot
  (ensure-system-test-defined)
  (def s-ctx (system/start-system {:services ["system-test"] :system-test {:param "param"}}))

  (is (thrown-with-msg?
       Exception #"No slot registered for :system-test/save"
       (system/call-slot s-ctx ::save {})))

  (is (thrown-with-msg?
       Exception #"No slot registered for :system-test/save"
       (save s-ctx  {})))

  (system/register-slot s-ctx ::save {:fn (fn [ctx res] (assoc res :id "id"))})

  (matcho/match (system/call-slot s-ctx ::save {}) {:id "id"})

  (matcho/match (save s-ctx {}) {:id "id"})

  (def s-ctx' (system/start-system {:services ["system-test" "module-a"]
                                    :system-test {:param "param"}}))

  (matcho/match (save s-ctx' {}) {:id "id" :ts "ts"})

  )

(deftest test-hooks
  (ensure-system-test-defined)
  (def context (system/start-system
                {:services ["system-test" "module-a"]
                 :system-test {:param "param"}}))

  (testing "hook call"
    (matcho/match
     (create context {:resourceType "Patient"})
     {:status :error, :errors [{:message "id is required"}]})

    (matcho/match
     (create context {:resourceType "Patient" :id "pt-1"})
     {:status :ok, :resource {:resourceType "Patient", :id "pt-1"}}))


  (testing "hook-reduce-context"

    (def ctx'' (process-middlewares context {:get "/user"}))
    (matcho/match (get-user ctx'') nil?)
    (matcho/match (get-client ctx'') nil?)

    (matcho/match (system/get-hooks context ::middleware) nil?)

    (system/register-hook context ::middleware ::mw1 {:fn #'set-user})
    (system/register-hook context ::middleware ::mw2 {:fn #'set-client})

    (matcho/match
     (system/get-hooks context ::middleware)
     {::mw1 {:fn #'system-test/set-user}
      ::mw2 {:fn #'system-test/set-client}})

    (def ctx' (process-middlewares context {:get "/user"}))

    (matcho/match (get-user ctx')   {:id "admin"})
    (matcho/match (get-client ctx') {:id "client"})


    )

  )

(deftest test-defmanifest
  (testing "when validator is an atom"
    (is (macroexpand '(system/defmanifest
                        {:config {:port {:type "integer"
                                         :validator pos-int?}}}))
        "Unexpected error during macro expansion"))
  (testing "when validator is a list"
    (is (macroexpand '(system/defmanifest
                        {:config {:filepath {:type "string"
                                             :validator (complement empty?)}}}))
        "Unexpected error during macro expansion")
    (is (macroexpand '(system/defmanifest
                        {:config {:url {:type "string"
                                        :validator #(clojure.string/starts-with? % "http")}}}))
        "Unexpected error during macro expansion"))
  (testing "when manifest doesn't conform to the schema"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Invalid manifest"
         (system/defmanifest {:description 123}))
        "Non-conforming description must throw")
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Invalid manifest"
         (system/defmanifest {:config "invalid"}))
        "Non-conforming config must throw"))
  
  (testing "type validation"
    (doseq [type ["integer" "number" "keyword" "string" "string[]" "boolean" "map"]]
      (is (system/defmanifest {:config {:my-field {:type type}}})
          "Unexpected error when validating type"))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Invalid manifest"
         (system/defmanifest {:config {:field-of-unsupported-type {:type "foobar"}}}))
        "Unsupported field type must throw")))

(deftest test-start-system
  (testing "config value validation"
    (system/defmanifest {:config {:number-field {:type "number"}}})
    (is (thrown-with-msg?
         Exception
         #"Invalid config"
         (system/start-system {:services [:system-test]
                               :system-test {:number-field "not a number"}}))
        "A field value of wrong type must throw")

    (system/defmanifest {:config {:port {:type "integer"}}})
    (is (system/start-system {:services [:system-test]
                              :system-test {:port 1234}})
        "Unexpected error when validating port")
    
    (system/defmanifest {:config {:data {:type "map"}}})
    (is (system/start-system {:services [:system-test]
                              :system-test {:data {:a 1 :b "c"}}})
        "Unexpected error when validating data")))

(deftest test-coerce
  (is (= {:port 123}
         (config/coerce {:port {:type "integer"}} {:port "123"})))
  (is (= {:ip "0.0.0.0"}
         (config/coerce {:ip {:type "string"}} {:ip "0.0.0.0"})))
  (is (= {:foobar :baz}
         (config/coerce {:foobar {:type "keyword"}} {:foobar "baz"})))
  (is (= {:flag true}
         (config/coerce {:flag {:type "boolean"}} {:flag "true"})))
  (is (= {:arr ["foo" "bar" "baz"]}
         (config/coerce {:arr {:type "string[]"}} {:arr "foo, bar, baz"})))
  (is (= {:conf {:foo true :bar "baz" :qux 123}}
         (config/coerce {:conf {:type "map"}}
                        {:conf "{\"foo\":true, \"bar\":\"baz\", \"qux\":123}"}))))

(deftest test-logging
  (ensure-system-test-defined)
  (let [context (system/start-system
                 {:services ["system-test"]
                  :system-test {:param ""}})]
    (testing "default log level (:info) produces output for both :info and :error log levels"
      (let [output (with-out-str
                     (system/error context "Hello from ERROR level"))]
        (is (str/includes? output "Hello from ERROR level")))

      (let [output (with-out-str
                     (system/info context "Hello from INFO level"))]
        (is (str/includes? output "Hello from INFO level"))))

    (testing "default log level (:info) produces no output for :debug level"
      (let [output (with-out-str
                     (system/debug context "Hello from INFO level"))]
        (is (empty? output))))

    (testing ":off log level disables logging completely"
      (let [context-without-logs (system/ctx-set-log-level context :off)
            output (with-out-str
                     (system/error context-without-logs "Hello from ERROR level")
                     (system/info  context-without-logs "Hello from INFO level")
                     (system/debug context-without-logs "Hello from DEBUG level"))]
        (is (empty? output))))))
