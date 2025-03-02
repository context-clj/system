(ns system.dev
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn git-directory?
  "Check if a directory is a git repository by looking for .git folder."
  [dir]
  (let [git-dir (io/file dir ".git")]
    (and (.exists git-dir) (.isDirectory git-dir))))

(defn git-pull
  "Execute git pull on the specified directory and return the result."
  [dir]
  (println "Pulling in repository:" (.getName (io/file dir)))
  (let [result (shell/with-sh-dir dir (shell/sh "git" "pull"))]
    (if (zero? (:exit result))
      (println "  Success:" (str/trim (:out result)))
      (throw (Exception. (str "  Failed:" (str/trim (:err result))))))
    result))

(defn update-libs []
  (doseq [file (.listFiles (io/file "./libs"))]
    (git-pull file)))

(comment
  (update-libs)

  )

