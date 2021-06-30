(ns com.widdindustries.tools.gauge
  "an API for periodically invoking registered callbacks
   - aka Gauges as they are known in jvm metric libraries"
  (:import (java.util.logging Logger Level)
           (java.time Duration)))

(def ^Logger logger (Logger/getLogger "com.widdindustries.tools.gauge"))

(defprotocol Gauge
  (gauge-name [_] "name should be unique within the registry")
  (log [_] "aka the callback. do whatever side-effecting stuff you like"))

(def registry (atom {}))

(defn deregister-all-gauges []
  (reset! registry {})
  nil)

(defn deregister-gauge [measurement-name]
  (swap! registry dissoc measurement-name)
  nil)

(defn register-gauge [g]
  (assert (satisfies? Gauge g) "arg is not a gauge")
  (swap! registry assoc (gauge-name g) g)
  nil)

(defn invoke-registered []
  (doseq [[measurement registered] @registry]
    (try
      (log registered)
      (catch Throwable t
        (.log logger Level/WARNING (str "problem invoking gauge: " measurement) t)))))

(def logging-thread nil)

(defn stop []
  (when logging-thread
    (.info logger "stopping gauge thread")
    (.interrupt ^Thread logging-thread)
    (alter-var-root #'logging-thread (constantly nil))))

(defn start [^Duration frequency]
  (stop)
  (let [^Thread thread
        (Thread.
          (fn []
            (try
              (loop []
                (.fine logger "invoking gauges")
                (invoke-registered)
                (Thread/sleep (.toMillis frequency))
                (recur))
              (catch InterruptedException _e)
              (catch Throwable t
                (.log logger Level/SEVERE "problem in gauge thread" t))))
          "Gauge thread")]
    (alter-var-root #'logging-thread (constantly thread))
    (.start thread)
    nil))

(comment 
  
  (start (Duration/ofSeconds 5))
  (register-gauge 
    (reify Gauge 
      (gauge-name [_] "test")
      (log [_]  (println "hello"))))
  (register-gauge
    (reify Gauge
      (gauge-name [_] "test2")
      (log [_]  (throw (Exception. "boom")))))
  (stop)
  (deregister-all-gauges)
  (deregister-gauge "test")
  (deregister-gauge "test2")
  
  )