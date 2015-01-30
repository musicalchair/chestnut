(ns chestnut.test.integration
  (:require [clojure.java.io :as io]
            [mistletoe.process :refer :all]
            [mistletoe.test :refer :all]
            [clj-webdriver.taxi :as browser]
            [environ.core :refer [env]]))

(def browser-type :chrome)

;; The path to the driver executable must be set by the
;; webdriver.chrome.driver system property; for more information, see
;; http://code.google.com/p/selenium/wiki/ChromeDriver. The latest
;; version can be downloaded from
;; http://chromedriver.storage.googleapis.com/index.html
(System/setProperty "webdriver.chrome.driver" (str (env :home) "/bin/chromedriver"))

(defn rm-rf [fname]
  (if (= fname "/")
    (throw (Exception. "Refusing to 'rm -rf /'")))
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (when (.exists f)
                 (io/delete-file f)))]
    (func func (io/file fname))))

(defn spawn [name dir cmd]
  (-> (process "sh" "-c" (str "cd " dir ";" cmd))
      (start)
      (start-pipe :in)
      (start-pipe :err)
      (log-chan (str name "       | "))
      (log-chan (str name " (err) | ") :errChan)))

(defmacro with-process
  "Make sure we don't leave subprocess around when an exception bubbles out"
  [[bind-var & spawn-args] & forms]
  `(let [~bind-var (spawn ~@spawn-args)]
     (try
       ~@forms
       (finally
         (kill ~bind-var)
         (wait-for ~bind-var) ; I want to notice if a process hangs
         ))))

(defmacro with-browser
  "Make sure we close the browser when an exception bubbles out"
  [address & forms]
  `(try
     (browser/set-driver! {:browser browser-type} ~address)
     ~@forms
     (finally
       (browser/close))))

(defn generate-new-app []
  (println "--> Generating app in /tmp/sesame-seed")
  (rm-rf "/tmp/sesame-seed")
  (wait-for (spawn "LEIN NEW" "/tmp" "lein new chestnut sesame-seed --snapshot")))

(defn first-element-text [selector]
  (browser/text (first (browser/css-finder selector))))

(defn test-basic []
  (generate-new-app)
  (println "--> Starting REPL")
  (with-process [repl "REPL" "/tmp/sesame-seed" "lein repl"]
    (expect repl #"sesame-seed\.server=>" 90)
    (write-str repl "(run)\n")
    (expect repl #"notifying browser that file changed")
    (write-str repl "(browser-repl)\n")
    (expect repl #"sesame-seed\.core=>")

    (with-browser "http://localhost:10555"
      (browser/wait-until #(= (first-element-text "h1") "Hello Chestnut!"))
      (write-str repl "(swap! app-state assoc :text \"Hello Test :)\")\n")
      (browser/wait-until #(= (first-element-text "h1") "Hello Test :)")))))

(defn -main []
  (test-basic))
