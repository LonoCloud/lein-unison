(ns leiningen.unison
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [file]]
            [leiningen.update-dependency :as d]
            [leiningen.voom :as v]
            [rewrite-clj.zip :as z]))

(defn full-project-name []
  (str (z/sexpr (z/right (z/down (z/of-file "project.clj"))))))

(defn release-version? [project]
  (not (re-matches #".+-SNAPSHOT" (:version project))))

(defn voom-version [project]
  (if (release-version? project)
    (:version project)
    (read-string (with-out-str (apply v/wrap project "pprint" [":version"])))))

(defn organization [git-uri]
  (last (re-find #".+:(.+)/.+.git" git-uri)))

(defn repo-name [git-uri]
  (last (re-find #".+/(.+).git" git-uri)))

(defn repo-dir [git-uri]
  (format "target/%s/%s" (organization git-uri) (repo-name git-uri)))

(defn git [dir & args]
  (let [prefix ["git" "--git-dir" (str dir "/.git") "--work-tree" dir]]
    (apply sh (vec (concat prefix args)))))

(defn clone-and-pull [git-uri branch]
  (let [dir (repo-dir git-uri)
        r-name (repo-name git-uri)]
    (if (.isDirectory (file dir))
      (do (println (format "Local repository for %s already exists. Pulling..." r-name))
          (git dir "checkout" branch)
          (sh "git" "-C" dir "pull" "origin" branch))
      (do (println (format "Local repository for %s doesn't exist. Cloning..." r-name))
          ;; Clone does not take -C, run without the `git` function.
          (sh "git" "clone" git-uri dir)))))

(defn update-commit-message [dep ver]
  (format "Update dependency %s to version %s.\n\nAutomatic commit by lein-traffic-control." dep ver))

(defn unison
  "Update dependencies of projects who depend on me."
  [project subtask-name & args]
  (cond (= subtask-name "update-projects")
        (let [version (voom-version project)]
          (doseq [r (:repos (:unison project))]
            (println)
            (println (format "Updating repo %s ..." (:git r)))
            (let [branch (or (:branch r) "master")
                  dir (repo-dir (:git r))
                  prj-name (full-project-name)
                  msg (update-commit-message prj-name version)]
              (clone-and-pull (:git r) branch)
              (println (format "Checking out branch: %s" branch))
              (git dir "checkout" branch)
              (println (format "Updating %s's %s dependency to version %s" (repo-name (:git r)) prj-name version))
              (d/update-dependency nil prj-name version (str dir "/project.clj"))
              (println "Commiting changes...")
              (git dir "commit" "-am" msg)
              (println "Pushing...")
              (git dir "push" "origin" branch)
              (println "Done."))))

        (= subtask-name "release-projects")
        (if (not (release-version? project))
          (println "Subtask release-projects is only available for release versions. Please remove -SNAPSHOT from your project version and try again.")
          (let [artifact-branch (first args)
                version (voom-version project)]
            (doseq [r (:repos (:unison project))]
              (println)
              (println (format "Releasing repo %s ..." (:git r)))
              (let [branch (or (:branch r) "master")
                    dir (repo-dir (:git r))]
                (clone-and-pull (:git r) branch)
                (println (format "Checking out branch: %s" branch))
                (git dir "checkout" branch)
                (println "Executing release script...")
                (sh "cd" dir "&&" "sh" (:release-script r) version artifact-branch)
                (println "Done.")))))

        :else
        (println (format "Subtask %s not found, exiting." subtask-name))))
