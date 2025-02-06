(ns only-stop-test
  (:require
   [clojure.test :refer [deftest is]]
   [system]))

(def system-test-stoped? (atom false))

;; system/defstart not defined intentionally

(system/defstop
  [context _state]
  (reset! system-test-stoped? true)
  (system/info context ::stop))

(deftest start-stop-test
  (system/defmanifest {:config {}})

  (def context (system/start-system
                {:services ["only-stop-test"]}))

  (is (= false @system-test-stoped?))
  (system/stop-system context)
  (is (= true @system-test-stoped?)))
