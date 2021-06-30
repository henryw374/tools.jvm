(ns com.widdindustries.tools.jvm
  (:require [clojure.stacktrace :as stck])
  (:import (java.lang.management ManagementFactory 
                                 ClassLoadingMXBean 
                                 BufferPoolMXBean 
                                 MemoryType 
                                 MemoryPoolMXBean 
                                 MemoryUsage 
                                 ThreadMXBean 
                                 ThreadInfo)))

(set! *warn-on-reflection* true)

(defn class-loading-snapshot []
  (let [^ClassLoadingMXBean b (ManagementFactory/getClassLoadingMXBean)]
    {"jvm.classes.loaded" (.getLoadedClassCount b)
     "jvm.classes.unloaded" (.getUnloadedClassCount b)}))

(defn memory-snapshot []
  {"buffer"
   (->> (ManagementFactory/getPlatformMXBeans BufferPoolMXBean)
        (mapv (fn [^BufferPoolMXBean b]
                {"id"                              (.getName b)
                 "jvm.buffer.count"                (.getCount b)
                 "jvm.buffer.memory.used"    (.getMemoryUsed b)
                 "jvm.buffer.total.capacity" (.getTotalCapacity b)}
                )))
   "memory"
   (->> (ManagementFactory/getPlatformMXBeans MemoryPoolMXBean)
        (mapv (fn [^MemoryPoolMXBean b]
                (let [mem-type (if (= MemoryType/HEAP (.getType b)) "heap" "nonheap")
                      ^MemoryUsage usage (.getUsage b)]
                  {"id"   (.getName b)
                   "area" mem-type
                   "jvm.memory.init"  (.getInit usage)
                   "jvm.memory.used"  (.getUsed usage)
                   "jvm.memory.committed"  (.getCommitted usage)
                   "jvm.memory.max" (.getMax usage)}))))})

(defn all-snapshots []
  {"classloading" (class-loading-snapshot)
   "memory" (memory-snapshot)})

(defn thread-dump
  ([] (thread-dump 100))
  ([max-depth]
   (let [^ThreadMXBean threadMXBean (ManagementFactory/getThreadMXBean)
         info (.getThreadInfo threadMXBean (.getAllThreadIds threadMXBean) ^int max-depth)]
     (for [^ThreadInfo threadInfo info]
       {"thread.name"  (.getThreadName threadInfo)
        "thread.state" (str (.getThreadState threadInfo))
        "thread.stack" (let [st (.getStackTrace threadInfo)]
                         (if-not (first st)
                           "[empty stack trace]"
                           (map (fn [ele]
                                  (with-out-str
                                    (stck/print-trace-element ele)))
                             st)))}))))


(comment 
  (all-snapshots)
  (thread-dump)
  )