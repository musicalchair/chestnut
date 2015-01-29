(ns mistletoe.process
  (:import [java.nio ByteBuffer])
  (:require [clojure.core.async :refer [go-loop <!! <! >! chan put! sliding-buffer timeout]]))

(defn process [& args]
  (let [argsAry (into-array String args)
        process (jnr.process.ProcessBuilder. argsAry)]
    {:processBuilder process
     :inBuf (ByteBuffer/allocate 2048)
     :outBuf (ByteBuffer/allocate 2048)}))

(defn directory [processMap dir]
  (.directory (:processBuilder processMap) dir)
  processMap)

(defn start [processMap]
  (let [process (.start (:processBuilder processMap))]
    (merge
     processMap
     {:process process
      :in (.getIn process)
      :err (.getErr process)
      :out (.getOut process)})))

(defn read-str [process & [stream]]
  (let [buf (:inBuf process)
        chan ((or stream :in) process)]
    (.clear buf)
    (.read chan buf)
    (String. (into-array Character/TYPE (take (.position buf) (.array buf))))))

(defn read-err [process]
  (read-str process :err))

(defn write-str [process exp]
  (let [buf (:outBuf process)
        chan (:out process)]
    (.clear buf)
    (.put buf (into-array Byte/TYPE (str exp)))
    (.flip buf)
    (.write chan buf)))

(defn start-pipe [process & [stream]]
  (let [c (chan 10)
        stream (or stream :in)]
    (go-loop []
      (>! c (read-str process stream))
      (recur))
    (assoc process (keyword (str stream "Chan")) c)))

(defn kill [{:process process} & [signal]]
  (.kill process (or signal 9)))

(defn waitFor [{:process process}]
  (.waitFor process))

(comment
  (def c (chan))
  (def buf (ByteBuffer/allocate 2048))
  (def out-buf (ByteBuffer/allocate 2048))


  (go-loop []
    (>! c (read-str (.getIn repl-process) buf))
    (recur))

  (go-loop []
    (>! c (read-str (.getErr repl-process) buf))
    (recur))

  (go-loop []
    (println "Got" (<! c))
    (recur))

  (write-str (.getOut repl-process) out-buf "(run)\n"))
