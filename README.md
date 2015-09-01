# lein-unison

A Leiningen plugin to push updates to projects that depend on a common project.

Use this if:

- You have multiple Leiningen projects, either in separate or the same Git repository.
- You are using [Voom](https://github.com/LonoCloud/lein-voom) versioning on your Leiningen projects.
- You have read and write access to all said repositories.
- Your Leiningen projects depend on one-another.
- You want parity across your builds. That is, if you have repo A, which depends on repo B, each time you make a change to B, you would like a build triggered on repo A with A's dependency updated with *exactly* the changes made to B - nothing more, nothing less.

## Usage

In `:plugins` in your `project.clj` for the project which you want to push updates *from*:

```clojure
[lonocloud/lein-unison "0.1.8"]
```

Also in your `project.clj`, name each project that depends on this project:

```clojure
(defproject my/cool-project "0.1.0-SNAPSHOT"
  ;;; ... Project stuff ...
  :unison
  {:repos
   [{:git "git@github.com:my-org/dependent-a.git"
     :release-script "script/release.sh"
     :branch "compatability"}
    {:git "git@github.com:my-org/dependent-b.git"
     :project-file "subproject-x/project.clj"
     :release-script "bin/release-the-project.sh"
     :branch "master"
     :merge "develop"}
    {:git "git@github.com:my-org/dependent-b.git"
     :project-file "subproject-y/project.clj"
     :release-script "script/release.sh"
     :branch "develop"}]}
  ;;; ... Project stuff ...
)
```

Notice that you can specify the same repository twice. Use `:project-file` to point to a `project.clj` file nested inside a repository.

### Snapshot release

When your main project ends in `"SNAPSHOT"`, run:

```
$ lein unison update-projects
```

For each repository in `:repos`, lein-unison will run through a series of
Git commands. Let's take `"dependent-a"`, for example. lein-unison will:

- Clone `"git@github.com:my-org/dependent-a.git"` over SSH and checkout branch `"compatibility"`.
- Update `dependent-a`'s dependency on `my/cool-project` to the current local Voom version for `my/cool-project`.
- Stage this change in the `dependent-a` repository.
- Commit to `dependent-a` with a message indicating the change.
- Push the change.

You can also specify `:merge`, which points to a branch. Before updating the dependency on branch `:branch`, `:merge` will be merged into `:branch`.

### Stable release

When your main projects doesn't end in `"SNAPSHOT"`, it is a release, so run:

```
$ lein unison release-projects <release-branch>
```

This command takes a branch name to store release commits in.
For each repository in `:repos`, lein-unison will run through a series of
Git commands. Let's take `"dependent-a"`, for example. lein-unison will:

- Clone `"git@github.com:my-org/dependent-a.git"` over SSH and checkout branch `"compatibility"`.
- Execute the `:release-script` file from `dependent-a`'s project root, passing it this project's version and the release-branch as arguments.

## License

Copyright © 2015 ViaSat

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
