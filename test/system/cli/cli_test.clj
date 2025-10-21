(ns system.cli.cli-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
   [helpers :refer [unload-all-modules-fixture]]
   [system.cli :as sut]))

(defn- simulate-cli-run-fixture [f]
  ;; Simulate loading "main" module
  ;; clj -M -i tests/system/cli/test_modules/module_a -m system
  (require 'system.cli.test_modules.module_a :reload)
  (f))

(use-fixtures :each (join-fixtures [unload-all-modules-fixture
                                    simulate-cli-run-fixture]))

(deftest test-cli-parse-args
  (testing "--help"
    (testing "Shows all available modules (discovered recursively from main module's deps)"
      (let [{:keys [summary]}
            (sut/parse-args ["--help"])

            available-modules-str
            (re-find #"Available modules:.*\n" summary)]
        (is
         (every? #(str/includes? available-modules-str %)
                 ["system.cli.test-modules.module-a"
                  "system.cli.test-modules.module-b"
                  "system.cli.test-modules.module-c"]))))

    (testing "Additionally passing --modules arguments shows help only for those modules"
      (let [modules
            ["system.cli.test-modules.module-a"
             "system.cli.test-modules.module-b"]

            {:keys [summary]}
            (sut/parse-args ["--modules" (pr-str modules) "--help"])

            available-modules-str
            (re-find #"Available modules:.*\n" summary)]
        (println summary)
        (is
         (every? #(str/includes? available-modules-str %)
                 modules))
        (is
         (not-any? #(str/includes? available-modules-str %)
                   ["system.cli.test-modules.module-c"
                    "system.cli.test-modules.module-d"])))))

  (testing "--modules"
    (testing "Running a system without --modules argument results in an error"
      (let [{:keys [errors]} (sut/parse-args [])]
        (is
         (seq
          (filter #(= % "Must provide at least one module using --modules argument")
                  errors)))))

    (testing "Can run a system with any 'reachable' module"
      (let [reachable-module
            "system.cli.test-modules.module-b"

            {:keys [errors]}
            (sut/parse-args ["--modules" reachable-module])]
        (is (empty? errors))))

    (testing "Cannot run a system with an 'unreachable' module"
      (let [unreachable-module
            "system.cli.test-modules.module-d"

            {:keys [errors]}
            (sut/parse-args ["--modules" unreachable-module])

            expected-error-message
            (str "No module named \"" unreachable-module "\" found.")]
        (is
         (seq
          (filter #(str/includes? % expected-error-message)
                  errors)))))

    (testing "Can pass multiple modules"
      (let [modules
            ["system.cli.test-modules.module-b"
             "system.cli.test-modules.module-a"]]
        (testing "as comma-separated values"
          (let [{:keys [errors]}
                (sut/parse-args ["--modules" (str/join "," modules)])]
            (is (empty? errors))))

        (testing "as an EDN vector"
          (let [{:keys [errors]}
                (sut/parse-args ["--modules" (pr-str modules)])]
            (is (empty? errors))))))

    (testing "Pass module params with --<module-name>.<param-name>"
      (testing "Must pass all required module params as command-line arguments"
        (let [modules
              ["system.cli.test-modules.module-c"
               "system.cli.test-modules.module-b"
               "system.cli.test-modules.module-a"]

              {:keys [errors]}
              (sut/parse-args ["--modules" (pr-str modules)])]
          (is
           (->> errors
                (filter #(str/starts-with? % "Missing argument:"))
                (seq)))))

      (testing "Can omit required module params if the module they belong to is not selected for start"
        (let [modules
              ["system.cli.test-modules.module-b"
               "system.cli.test-modules.module-a"]

              {:keys [errors]}
              (sut/parse-args ["--modules" (pr-str modules)])]
          (is (empty? errors))))

      (testing "Supported module param types"
        (testing "string"
          (doseq [expect ["foobar" "123" "true"]
                  :let [{:keys [options errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-1"
                                         expect])]]
            (is (empty? errors))
            (let [actual (:system.cli.test-modules.module-a.param-1 options)]
              (is (= expect actual)))))

        (testing "string[]"
          (testing "Can pass as comma-separated values"
            (let [expect
                  ["foobar" "123" "true"]

                  {:keys [options errors]}
                  (sut/parse-args ["--modules"
                                   "system.cli.test-modules.module-a"

                                   "--system.cli.test-modules.module-a.param-2"
                                   (str/join "," expect)])]
              (is (empty? errors))
              (let [actual (:system.cli.test-modules.module-a.param-2 options)]
                (is (= expect actual)))))

          (testing "Can pass as an EDN vector"
            (let [expect
                  ["foobar" "123" "true"]

                  {:keys [options errors]}
                  (sut/parse-args ["--modules"
                                   "system.cli.test-modules.module-a"

                                   "--system.cli.test-modules.module-a.param-2"
                                   (pr-str expect)])]
              (is (empty? errors))
              (let [actual (:system.cli.test-modules.module-a.param-2 options)]
                (is (= expect actual))))))

        (testing "integer"
          (doseq [expect [-1 0 1]
                  :let [{:keys [options errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-3"
                                         (str expect)])]]
            (is (empty? errors))
            (let [actual (:system.cli.test-modules.module-a.param-3 options)]
              (is (= expect actual))))

          (doseq [bad-value ["foobar" "true"]
                  :let [{:keys [errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-3"
                                         bad-value])]]
            (->> errors
                 (filter #(str/includes? % "Expected type: integer"))
                 (seq))))

        (testing "number"
          (doseq [expect [3.14159 -1 0 1]
                  :let [{:keys [options errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-4"
                                         (str expect)])]]
            (is (empty? errors))
            (let [actual (:system.cli.test-modules.module-a.param-4 options)]
              (is (= expect actual))))

          (doseq [bad-value ["foobar" "true"]
                  :let [{:keys [errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-4"
                                         bad-value])]]
            (->> errors
                 (filter #(str/includes? % "Expected type: number"))
                 (seq))))

        (testing "keyword"
          (doseq [expect [:foo :bar :foobar]
                  :let [{:keys [options errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-5"
                                         (name expect)])]]
            (is (empty? errors))
            (let [actual (:system.cli.test-modules.module-a.param-5 options)]
              (is (= expect actual)))))

        (testing "boolean"
          (doseq [expect [true false]
                  :let [{:keys [options errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-6"
                                         (str expect)])]]
            (is (empty? errors))
            (let [actual (:system.cli.test-modules.module-a.param-6 options)]
              (is (= expect actual))))

          (doseq [bad-value [3.14159 1 "foo"]
                  :let [{:keys [errors]}
                        (sut/parse-args ["--modules"
                                         "system.cli.test-modules.module-a"

                                         "--system.cli.test-modules.module-a.param-6"
                                         (str bad-value)])]]
            (->> errors
                 (filter #(str/includes? % "Expected type: boolean"))
                 (seq))))

        (testing "map"
          (testing "Pass as a JSON object"
            (doseq [expect [{:foo "bar" :3.14159 42}
                            {}]
                    :let [{:keys [options errors]}
                          (sut/parse-args ["--modules"
                                           "system.cli.test-modules.module-a"

                                           "--system.cli.test-modules.module-a.param-7"
                                           (json/generate-string expect)])]]
              (is (empty? errors))
              (let [actual (:system.cli.test-modules.module-a.param-7 options)]
                (is (= expect actual)))))

          (testing "Pass as an EDN map"
            (doseq [expect [{:foo  "bar" :3.14159  42}
                            {"foo" "bar" "3.14159" 42}
                            {}]
                    :let [{:keys [options errors]}
                          (sut/parse-args ["--modules"
                                           "system.cli.test-modules.module-a"

                                           "--system.cli.test-modules.module-a.param-7"
                                           (pr-str expect)])]]
              (is (empty? errors))
              (let [actual (:system.cli.test-modules.module-a.param-7 options)]
                (is (= expect actual)))))

          (testing "Fails on invalid JSON and EDN"
            (doseq [bad-value ["foobar" "123" "true" "{1: 2}" "{]"]
                    :let [{:keys [errors]}
                          (sut/parse-args ["--modules"
                                           "system.cli.test-modules.module-a"

                                           "--system.cli.test-modules.module-a.param-7"
                                           bad-value])]]
              (->> errors
                   (filter #(str/includes? % "Expected type: map"))
                   (seq)))))))))


(deftest test-cli-options->system-config
  (testing "Can pass multiple modules. They will start in the passed order"
    (let [modules
          ["system.cli.test-modules.module-b"
           "system.cli.test-modules.module-a"]

          {:keys [options errors]}
          (sut/parse-args ["--modules" modules])]
      (is (empty? errors))

      (let [system-config (sut/options->system-config options)]
        (is
         (= modules
            (:services system-config)))))))
