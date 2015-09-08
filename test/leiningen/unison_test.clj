(ns leiningen.unison-test
  (:require [clojure.java.shell :refer [sh]]
            [rewrite-clj.zip :as z]))

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
  (doseq [f followers]
    (initialize-repo f)))

#_(initialize-repos "a" ["b" "c" "d"])

(defn depend-on-project [leader follower]
  (let [f-name (str "target/" follower "/project.clj")]
    (some-> (z/of-file f-name)
            (z/find-value z/next 'defproject)
            (z/find-value :dependencies)
            z/right
            (z/append-child [(symbol leader) "0.1.0-SNAPSHOT"])
            z/root
            ((fn [x] (spit f-name x))))))

(depend-on-project "a" "b")
