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

(defn repo-name [git-uri]
  (or (last (re-find #".+/(.+).git" git-uri))
      (last (re-find #".+/(.+)" git-uri))))

(defn repo-dir [git-uri]
  (format "target/%s" (repo-name git-uri)))

(defn run-sh [& args]
  (let [rets (apply sh args)]
    (when (seq (:err rets))
      (println (:err rets)))
    rets))

(defn git [dir & args]
  (let [prefix ["git" "-C" dir]]
    (apply run-sh (vec (concat prefix args)))))

(defn clone-and-pull [git-uri branch]
  (let [dir (repo-dir git-uri)
        r-name (repo-name git-uri)]
    (if (.isDirectory (file dir))
      (do (println (format "Local repository for %s already exists. Pulling..." r-name))
          (git dir "checkout" branch)
          (run-sh "git" "-C" dir "pull" "--all"))
      (do (println (format "Local repository for %s doesn't exist. Cloning..." r-name))
          ;; Clone does not take -C, run without the `git` function.
          (run-sh "git" "clone" git-uri dir)
          (when-not (.endsWith git-uri ".git")
            (println "Local repository detected. Switching remote origin...")
            (git dir "remote" "remove" "origin")
            (git dir "remote" "add" "origin" (str git-uri ".git")))))))

(defn project-path [repo dir]
  (if-let [p (:project-file repo)]
    (str dir "/" p)
    (str dir "/project.clj")))

(defn update-commit-message [dep ver]
  (format "Update dependency %s to version %s.\n\nAutomatic commit by lein-unison." dep ver))

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

              (when (:merge r)
                (println (format "Checking out branch: %s" (:merge r)))
                (git dir "checkout" (:merge r))
                (git dir "pull" "origin" (:merge r)))

              (println (format "Checking out branch: %s" branch))
              (git dir "checkout" branch)
              (git dir "pull" "origin" branch)

              (when (:merge r)
                (println (format "Merging %s into %s" (:merge r) branch))
                (git dir "merge" (:merge r) "-X" "theirs"))

              (println (format "Updating %s's %s dependency to version %s" (repo-name (:git r)) prj-name version))
              (d/update-dependency nil prj-name version (project-path r dir))
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
              (let [branch (or (:release-branch r) "master")
                    dir (repo-dir (:git r))]
                (clone-and-pull (:git r) branch)
                (println (format "Checking out branch: %s" branch))
                (git dir "checkout" branch)
                (println "Executing release script...")
                (run-sh (str dir "/" (:release-script r)) version artifact-branch)
                (println "Done.")))))

        :else
        (println (format "Subtask %s not found, must be either update-projects or release-projects. Exiting." subtask-name))))
