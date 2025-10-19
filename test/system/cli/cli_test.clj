(ns system.cli.cli-test
  (:require
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
                 ["module-a" "module-b" "module-c"])))))

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

    (testing "Module params"
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
          (is (empty? errors)))))))

#_(deftest test-cli-options->system-config
    (testing "Can pass multiple modules. They will start in the passed order"
      (let [modules
            ["system.cli.test-modules.module-b"
             "system.cli.test-modules.module-a"]

            {:keys [errors]}
            (sut/parse-args ["--modules" modules])]
        (is (empty? errors)))))
