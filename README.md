# tools.jvm

clojure tools for getting information about the jvm runtime

Dependency-free

This has been tested on various java 11 runtimes.

# Usage 


[![Clojars Project](https://img.shields.io/clojars/v/com.widdindustries/tools.jvm.svg)](https://clojars.org/com.widdindustries/tools.jvm)


```clojure

(require '[com.widdindustries.tools.jvm :as jvm])

;get data about memory usage, classloading (and not yet... gc, threads)
(jvm/all-snapshots)

; thread dump as data
(jvm/thread-dump)

```

There are many other bits of interesting data in jvm objects from the standard library, hidden by ESLs. 

PRs welcome to add more functions to get data out of those


# Release

create a git tag.

`make install VERSION=your-tag` (this installs in ~/.m2 - check that things look ok)

`make deploy VERSION=your-tag`  - you need to have set up clojars credentials as per https://github.com/applied-science/deps-library

`git push origin new-tag-name`
