(ns system-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcho.core :as matcho]
            [clojure.spec.alpha :as s]
            [system]))

(s/def ::resourceType string?)
(s/def ::resource-map (s/keys :req-un [::resourceType]))

(s/def ::error-message string?)
(s/def ::error-message-map (s/keys :req-un [::message]))
(s/def ::validation-errors (s/coll-of ::message-map :kind vector?))

(system/defmanifest
  {:config {:param {:required true :type "string"}}
   :define-hook {::validate   {:args [::resource-map] :result ::validation-errors}
                 ::middleware {:args [::request]}}
   :define-slot {::save       {:args [::resource-map] :result ::resource-map}}
   :events {:define {}
            :subscribe {}}})


(system/defstart
  [context config]
  (system/info context ::start config)
  {:state :v1 :config config})

(system/defstop
  [context state]
  (system/info context ::stop))

(deftest basic-test
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

  (system/stop-system context)
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
