# tools.jvm

clojure tools for getting information about the jvm runtime

# Usage 

```clojure

(require '[com.widdindustries.tools.jvm :as jvm])

;get data about memory usage, classloading (and not yet... gc, threads)
(jvm/all-snapshots)

; thread dump as data
(jvm/thread-dump)

```


# Release

create a git tag.

`make install VERSION=your-tag` (this installs in ~/.m2 - check that things look ok)

`make deploy VERSION=your-tag`  - you need to have set up clojars credentials as per https://github.com/applied-science/deps-library

`git push origin new-tag-name`
