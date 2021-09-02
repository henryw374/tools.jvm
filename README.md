# tools.jvm

clojure tools for getting information about the jvm runtime as data

Dependency-free. 

There are many other bits of interesting data in jvm objects from the standard library (e.g. thread pool stats), hidden by ESLs and not yet in this library, PRs welcome to add more functions to get data out of those

This has been tested on various java 11 runtimes.

Also included is a 'gauge' namespace, which provides tools for periodically calling registered functions. This can be used as a replacement for micrometer or dropwizard metrics.

The functions that return gc, memory and thread data are all based on micrometer's
implementation. Unfortunately the only way to use micrometer's versions is via 
their Gauge/Meter/Timer API.

# Usage 


[![Clojars Project](https://img.shields.io/clojars/v/com.widdindustries/tools.jvm.svg)](https://clojars.org/com.widdindustries/tools.jvm)


```clojure

(require '[com.widdindustries.tools.jvm :as jvm])

;get data about memory usage, classloading and threads
(jvm/all-snapshots)

; thread dump as data
(jvm/thread-dump)

; log garbage collections
(listen-to-gc println)
(stop-listening-to-gc)


```


# Release

create a git tag.

`make install VERSION=your-tag` (this installs in ~/.m2 - check that things look ok)

`make deploy VERSION=your-tag`  - you need to have set up clojars credentials as per https://github.com/applied-science/deps-library

`git push origin new-tag-name`
