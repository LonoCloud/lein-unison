(ns leiningen.unison-test
  (:require [clojure.java.shell :refer [sh]]))

(defn initialize-repo [repo-name]
  (sh "rm" "-rf" (str "target/" repo-name))
  (sh "rm" "-rf" (str "target/" repo-name ".git"))
  (sh "mkdir" "target")
  (sh "git" "-C" "target" "init" "--bare" (str repo-name ".git"))
  (sh "lein" "new" repo-name "--to-dir" (str "target/" repo-name))
  (sh "git" "-C" "target" "init" repo-name)
  (sh "git" "-C" (str "target/" repo-name) "remote" "add" "origin" (str "../" repo-name ".git")))

(defn initialize-repos [leader followers]
  (initialize-repo leader)
  (doseq [f followers] (initialize-repo f)))

(initialize-repos "a" [])
