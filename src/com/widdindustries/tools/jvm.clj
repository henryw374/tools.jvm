(ns com.widdindustries.tools.jvm
  (:require [clojure.stacktrace :as stck]
            [clojure.string :as string])
  (:import (java.lang.management ManagementFactory
                                 ClassLoadingMXBean
                                 BufferPoolMXBean
                                 MemoryType
                                 MemoryPoolMXBean
                                 MemoryUsage
                                 ThreadMXBean
                                 ThreadInfo GarbageCollectorMXBean)
           [java.util.logging Logger Level]
           (javax.management NotificationEmitter NotificationListener Notification NotificationFilter)
           (com.sun.management GarbageCollectionNotificationInfo GcInfo)
           (javax.management.openmbean CompositeData)))

(def ^Logger logger (Logger/getLogger "com.widdindustries.tools.jvm"))

(set! *warn-on-reflection* true)

(defn isHeap [^MemoryPoolMXBean b]
  (= MemoryType/HEAP (.getType b)))

(def isGenerationalGcConfigured
  (memoize
    (fn []
      (->> (ManagementFactory/getMemoryPoolMXBeans)
           (filter (fn [^MemoryPoolMXBean b]
                     (and (isHeap b)
                       (not (string/includes? (.getName b) "tenured")))))
           count
           (< 1)))))


(defn isConcurrentPhase [cause]
  (= "No GC" cause))

(defn isYoungGenPool [^MemoryPoolMXBean b]
  (string/ends-with? (.getName b) "Eden Space"))

(defn isOldGenPool [^MemoryPoolMXBean b]
  (or
    (string/ends-with? (.getName b) "Old Gen")
    (string/ends-with? (.getName b) "Tenured Gen")))

(defn get-old-gen []
  (->> (ManagementFactory/getMemoryPoolMXBeans)
       (some (fn [^MemoryPoolMXBean b]
               (and (isHeap b) (isOldGenPool b) b)))))

(def knownCollectors
  {
   "ConcurrentMarkSweep",    :old
   "Copy",                   :young
   "G1 Old Generation",      :old
   "G1 Young Generation",    :young
   "MarkSweepCompact",       :old
   "PS MarkSweep",           :old
   "PS Scavenge",            :young
   "ParNew",                 :young
   "global",                 :old
   "scavenge",               :young
   "partial gc",             :young
   "global garbage collect", :old
   "Epsilon",                :old
   })

(defn fromGcName [gc-name]
  (get knownCollectors gc-name :unknown))

(defn isAllocationPool [^MemoryPoolMXBean n]
  (when-let [pool-name (.getName n)]
    (or (string/ends-with? pool-name "Eden Space")
      (= "Shenandoah" pool-name)
      (= "ZHeap" pool-name)
      (= "JavaHeap" pool-name)
      (string/ends-with? pool-name "nursery-allocate")
      (string/ends-with? pool-name "-eden"))))

(defn isLongLivedPool [^MemoryPoolMXBean n]
  (when-let [pool-name (.getName n)]
    (or
      (string/ends-with? pool-name "Old Gen")
      (string/ends-with? pool-name "Tenured Gen")
      (string/ends-with? pool-name "balanced-old")
      (.contains pool-name "tenured")
      (= "Shenandoah" pool-name)
      (= "ZHeap" pool-name)
      (= "JavaHeap" pool-name))))

(def pool-names
  (memoize (fn []
             (reduce
               (fn [r ^MemoryPoolMXBean n]
                 (cond-> r
                   (isAllocationPool n) (assoc :allocation-pool-name (.getName n))
                   (isLongLivedPool n) (update :long-lived-pool-names conj (.getName n))
                   )
                 )
               {:long-lived-pool-names []}
               (ManagementFactory/getMemoryPoolMXBeans))
             )))

#_(defn maxDataSize
  "Max size of long-lived heap memory pool"
  []
  (->> (ManagementFactory/getMemoryPoolMXBeans)
       (filter isLongLivedPool)
       (map (fn [^MemoryPoolMXBean n]
              (-> n (.getUsage) (.getMax))))
       (apply +)))

(defn usage [^MemoryUsage usage]
  {"jvm.memory.init"      (.getInit usage)
   "jvm.memory.used"      (.getUsed usage)
   "jvm.memory.committed" (.getCommitted usage)
   "jvm.memory.max"       (.getMax usage)})

(defn gc-listener [cb]
  (reify NotificationListener
    (^void handleNotification [_this ^Notification notification _ref]
      (let [^CompositeData cd (.getUserData notification)
            notificationInfo (GarbageCollectionNotificationInfo/from cd)
            ^String gcCause (.getGcCause notificationInfo)
            ^String gcAction (.getGcAction notificationInfo)
            ^GcInfo gcInfo (.getGcInfo notificationInfo)
            duration (.getDuration gcInfo)]
        (cb
          {"phase"       (if (isConcurrentPhase gcCause) "jvm.gc.concurrent.phase.time"
                                                         "jvm.gc.pause.time")
           "time_millis" duration
           "action", gcAction, 
           "cause", gcCause}))
      nil)))

(def listeners (atom []))

(defn listen-to-gc
  ([f]
   (listen-to-gc listeners f))
  ([listeners f]
   (doseq [^GarbageCollectorMXBean mbean
           (ManagementFactory/getGarbageCollectorMXBeans)]
     (if-not (instance? NotificationEmitter mbean)
       (.log logger Level/INFO (str "garbage bean " (.getName mbean) " is not a NotificationEmitter"))
       (let [listener (gc-listener f)]
         (swap! listeners conj {:emitter mbean :listener listener})
         (.addNotificationListener ^NotificationEmitter mbean
           listener
           (reify NotificationFilter
             (^boolean isNotificationEnabled [_this ^Notification notification]
               (= "com.sun.management.gc.notification" (.getType notification))))
           nil))))))

(defn stop-listening-to-gc
  ([] (stop-listening-to-gc listeners))
  ([listeners]
   (doseq [{:keys [emitter listener]} @listeners]
     (.removeNotificationListener ^NotificationEmitter emitter listener))
   (reset! listeners [])))

(comment 
  (listen-to-gc println)
  (stop-listening-to-gc)
  
  )

;;;;;;;;;;;;;;;;;;;;;;;;;

(defn class-loading-snapshot []
  (let [^ClassLoadingMXBean b (ManagementFactory/getClassLoadingMXBean)]
    {"jvm.classes.loaded"   (.getLoadedClassCount b)
     "jvm.classes.unloaded" (.getUnloadedClassCount b)}))

(defn memory-snapshot []
  {"buffer"
   (->> (ManagementFactory/getPlatformMXBeans BufferPoolMXBean)
        (mapv (fn [^BufferPoolMXBean b]
                {"id"                        (.getName b)
                 "jvm.buffer.count"          (.getCount b)
                 "jvm.buffer.memory.used"    (.getMemoryUsed b)
                 "jvm.buffer.total.capacity" (.getTotalCapacity b)})))
   "memory"
   (->> (ManagementFactory/getPlatformMXBeans MemoryPoolMXBean)
        (mapv (fn [^MemoryPoolMXBean b]
                (let [mem-type (if (= MemoryType/HEAP (.getType b)) "heap" "nonheap")]
                  (merge
                    (usage (.getUsage b))
                    {"id"                   (.getName b)
                     "area"                 mem-type})))))})

(defn all-snapshots []
  {"classloading" (class-loading-snapshot)
   "memory"       (memory-snapshot)})

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