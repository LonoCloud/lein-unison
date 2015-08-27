(ns leiningen.unison
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [file]]
            [leiningen.update-dependency :as d]
            [leiningen.voom :as v]
            [rewrite-clj.zip :as z]))

(defn full-project-name []
  (str (z/sexpr (z/right (z/down (z/of-file "project.clj"))))))

(defn voom-version [project]
  (read-string (with-out-str (apply v/wrap project "pprint" [":version"]))))

(defn organization [git-uri]
  (last (re-find #".+:(.+)/.+.git" git-uri)))

(defn repo-name [git-uri]
  (last (re-find #".+/(.+).git" git-uri)))

(defn repo-dir [git-uri]
  (format "target/%s/%s" (organization git-uri) (repo-name git-uri)))

(defn git [dir & args]
  (let [prefix ["git" "--git-dir" (str dir "/.git") "--work-tree" dir]]
    (apply sh (concat prefix args))))

(defn clone-and-pull [git-uri]
  (let [dir (repo-dir git-uri)
        r-name (repo-name git-uri)]
    (if (.isDirectory (file dir))
      (do (println (format "Local repository for %s already exists. Pulling..." r-name))
          (git dir "pull"))
      (do (println (format "Local repository for %s doesn't exist. Cloning..." r-name))
          (git dir "clone" git-uri dir)))))

(defn commit-message [dep ver]
  (format "Update dependency %s to version %s.\n\nAutomatic commit by lein-traffic-control." dep ver))

(defn unison
  "Update dependencies of projects who depend on me."
  [project subtask-name & args]
  (cond (= subtask-name "update-projects")
        (let [version (voom-version project)]
          (doseq [r (:repos (:unison project))]
            (println)
            (println (format "Updating repo %s ..." (:git r)))
            (let [repo (clone-and-pull (:git r))
                  branch (or (:branch r) "master")
                  dir (repo-dir (:git r))
                  prj-name (full-project-name)
                  version (voom-version project)
                  msg (commit-message prj-name version)]
              (println (format "Checking out branch: %s" branch))
              (git dir "checkout" branch)
              (println (format "Updating %s's %s dependency to version %s" (repo-name (:git r)) prj-name version))
              (d/update-dependency nil prj-name version (str dir "/project.clj"))
              (println "Commiting changes...")
              (git dir "commit" "-am" msg)
              (println "Pushing...")
              (git dir "push" "origin" branch)
              (println "Done."))))

        :else
        (println (format "Subtask %s not found, exiting." subtask-name))))