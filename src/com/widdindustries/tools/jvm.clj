(ns com.widdindustries.tools.jvm
  (:require [clojure.stacktrace :as stck])
  (:import (java.lang.management ManagementFactory
                                 ClassLoadingMXBean
                                 BufferPoolMXBean
                                 MemoryType
                                 MemoryPoolMXBean
                                 MemoryUsage
                                 ThreadMXBean
                                 ThreadInfo GarbageCollectorMXBean RuntimeMXBean)
           [java.util.logging Logger Level]
           (javax.management NotificationEmitter NotificationListener Notification NotificationFilter)
           (com.sun.management GarbageCollectionNotificationInfo GcInfo)
           (javax.management.openmbean CompositeData)))

(def ^Logger logger (Logger/getLogger "com.widdindustries.tools.jvm"))

(set! *warn-on-reflection* true)

(defn isConcurrentPhase [cause]
  (= "No GC" cause))

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
           "action",     gcAction,
           "cause",      gcCause}))
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

(defn thread-snapshot []
  (let [^ThreadMXBean bean (ManagementFactory/getThreadMXBean)]
    {"jvm.threads.peak"        (.getPeakThreadCount bean)
     "jvm.threads.daemon"      (.getDaemonThreadCount bean)
     "jvm.threads.live"        (.getThreadCount bean)
     "jvm.threads.state-count" (->> (.getThreadInfo bean (.getAllThreadIds bean))
                                    (keep #(try (str (.getThreadState ^ThreadInfo %))
                                               (catch Throwable _t)))
                                    frequencies)}))

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
                    {"id"   (.getName b)
                     "area" mem-type})))))})

(defn all-snapshots []
  {"classloading" (class-loading-snapshot)
   "memory"       (memory-snapshot)
   "threads"      (thread-snapshot)})

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

(defn jvm-deets []
  (let [^RuntimeMXBean runtime-bean (ManagementFactory/getRuntimeMXBean)]
    {:spec-version              (.getSpecVersion runtime-bean)
     :vm-version                (.getVmVersion runtime-bean)
     :management-spec-version   (.getManagementSpecVersion runtime-bean)
     :name                      (.getName runtime-bean)
     :spec-name                 (.getSpecName runtime-bean)
     :boot-class-path-supported (.isBootClassPathSupported runtime-bean)
     :class-path                (.getClassPath runtime-bean)
     :start-time                (.getStartTime runtime-bean)
     :input-arguments           (.getInputArguments runtime-bean)
     :system-properties         (.getSystemProperties runtime-bean)
     :pid                       (.getPid runtime-bean)
     :uptime                    (.getUptime runtime-bean)
     :spec-vendor               (.getSpecVendor runtime-bean)
     :vm-name                   (.getVmName runtime-bean)
     :boot-class-path           (if (.isBootClassPathSupported runtime-bean) (.getBootClassPath runtime-bean) "Not Supported")
     :library-path              (.getLibraryPath runtime-bean)
     :vm-vendor                 (.getVmVendor runtime-bean)}))

(comment
  (jvm-deets)
  (all-snapshots)
  (thread-dump)

  (listen-to-gc println)
  (stop-listening-to-gc)

  )