(ns system-test
  (:require [clojure.test :refer [deftest testing is]]
            [matcho.core :as matcho]
            [system]))

(system/defmanifest
  {:config {:param {:required true :type "string"}}
   :hooks {:define {}
           :subscribe {}}
   :events {:define {}
            :subscribe {}}})

(system/defstart
  [context config]
  (system/info context ::start)
  {:state :v1})

(system/defstop
  [context state]
  (system/info context ::stop)

  )


(deftest basic-test
  (def context (system/start-system
                {:services ["system-test"]
                 :system-test {:param "param"}}))

  context

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


  )
