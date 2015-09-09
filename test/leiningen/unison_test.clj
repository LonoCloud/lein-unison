(ns leiningen.unison-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.shell :refer [sh]]
            [rewrite-clj.zip :as z]))

(defn commit-repo [r]
  (println (format "[%s] Making the first commit..." r))
  (sh "git" "-C" (str "target/" r) "add" ".")
  (sh "git" "-C" (str "target/" r) "commit" "-m" "Automatic commit."))

(defn initialize-repo [repo-name]
  (println (format "[%s] Clearing away old files..." repo-name))
  (sh "rm" "-rf" (str "target/" repo-name))
  (sh "rm" "-rf" (str "target/" repo-name ".git"))
  (sh "mkdir" "target")

  (println (format "[%s] Creating a new bare Git repo..." repo-name))
  (sh "git" "-C" "target" "init" "--bare" (str repo-name ".git"))

  (println (format "[%s] Building a new Leiningen project..." repo-name))
  (sh "lein" "new" (str repo-name "/" repo-name) "--to-dir" (str "target/" repo-name))

  (println (format "[%s] Adding local Git push/pull paths..." repo-name))
  (sh "git" "-C" "target" "init" repo-name)
  (sh "git" "-C" (str "target/" repo-name) "remote" "add" "origin" (str "../" repo-name ".git"))

  (commit-repo repo-name))

(defn depend-on-project [leader follower]
  (println (format "[%s] Adding dependency on project %s..." follower leader))
  (let [f-name (str "target/" follower "/project.clj")]
    (some-> (z/of-file f-name)
            (z/find-value z/next 'defproject)
            (z/find-value :dependencies)
            z/right
            (z/append-child ^{:voom {:repo (str "../" leader) :branch "master"}}
                            [(symbol (str leader "/" leader)) "0.1.0-SNAPSHOT"])
            z/root
            ((fn [x] (spit f-name x))))
    (spit (format "target/%s/release.sh" follower) "cd \"$(dirname \"$0\")\" && touch released.txt")
    (sh "chmod" "+x" (format "target/%s/release.sh" follower))))

(defn add-unison-to-project [leader followers]
  (let [f-name (str "target/" leader "/project.clj")
        deps (mapv (fn [d] {:git (format "%s/target/%s" (System/getProperty "user.dir") d)
                           :release-script "release.sh"})
                   followers)]
    (spit (format "target/%s/run-update.sh" leader) "cd \"$(dirname \"$0\")\" && lein unison update-projects")
    (spit (format "target/%s/run-release.sh" leader) "cd \"$(dirname \"$0\")\" && lein unison release-projects 0.1.0 0.1.x")

    (sh "chmod" "+x" (format "target/%s/run-update.sh" leader))
    (sh "chmod" "+x" (format "target/%s/run-release.sh" leader))
    (some-> (z/of-file f-name)
            (z/find-value z/next 'defproject)

            z/rightmost
            (z/insert-right :plugins)
            z/rightmost
            (z/insert-right [['lonocloud/lein-unison "0.1.11-SNAPSHOT"]])

            (z/find-value z/next :plugins)
            (z/prepend-newline)
            (z/prepend-space 2)

            z/rightmost
            (z/insert-right :unison)
            z/rightmost
            (z/insert-right {:repos deps})

            (z/find-value z/next :unison)
            (z/prepend-newline)
            (z/prepend-space 2)

            z/root
            ((fn [x] (spit f-name x))))))

(defn dep-version [repo]
  (last
   (last
    (some-> (z/of-file (format "target/%s/project.clj" repo))
            (z/find-value z/next :dependencies)
            z/right
            z/sexpr))))

(defn voom-dep [repo]
  (last (re-find #"\-g(.*)" (dep-version repo))))

(defn update-to-release [repo]
  (let [f-name (format "target/%s/project.clj" repo)]
    (some-> (z/of-file f-name)
            (z/find-value z/next 'defproject)
            z/next
            z/next
            (z/edit (constantly "0.1.0"))
            z/root
            ((fn [x] (spit f-name x))))))

(defn initialize-repos [leader followers]
  (println "Building new repository set...")
  (initialize-repo leader)
  (add-unison-to-project leader followers)
  (commit-repo leader)
  (doseq [f followers]
    (initialize-repo f)
    (depend-on-project leader f)
    (commit-repo f))
  (println "Done"))

(deftest test-update-projects
  (initialize-repos "a" ["b"])
  (is (= "0.1.0-SNAPSHOT" (dep-version "b")))
  (sh "target/a/run-update.sh")
  (sh "git" "-C" "target/b" "pull" "origin" "master")
  (let [main-version (.trim (:out (sh "git" "-C" "target/a" "rev-parse" "--short" "HEAD")))
        dep-version (voom-dep "b")]
    (is (= main-version dep-version))))

(deftest test-release-projects
  (initialize-repos "a" ["b"])
  (update-to-release "a")
  (sh "target/a/run-release.sh")
  (is (.exists (clojure.java.io/file "target/a/target/b/released.txt"))))
