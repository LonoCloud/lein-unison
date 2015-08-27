# lein-unison

A Leiningen plugin to automatically update projects that depend on a common project.

Use this if:

- You have multiple Leiningen projects in separate repositories.
- You are using [Voom](https://github.com/LonoCloud/lein-voom) versioning on your Leiningen projects
- You have read and write access to all said repositories.
- Your Leiningen projects depend on one-another.
- You want parity across your builds. That is, if you have repo A, which depends on repo B, each time you make a change to B, you would like a build triggered on repo A with A's dependency updated with *exactly* the changes made to B - nothing more, nothing less.

## Usage

In `:plugins` in your `project.clj`:

```clojure
[lonocloud/lein-unison "0.1.2"]
```

Also in your `project.clj`, name each of the project's that depends on this project:

```clojure
(defproject my/cool-project "0.1.0-SNAPSHOT"
  ;;; ... Project stuff ...
  :unison
  {:repos
   [{:git "git@github.com:my-org/dependent-a.git" :branch "compatability"}
    {:git "git@github.com:my-org/dependent-b.git" :branch "master"}
    {:git "git@github.com:my-org/dependent-c.git" :branch "develop"}]}
  ;;; ... Project stuff ...
)
```

Now run:

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

## License

Copyright © 2015 ViaSat

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
