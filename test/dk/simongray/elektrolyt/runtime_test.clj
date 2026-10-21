(ns dk.simongray.elektrolyt.runtime-test
  "The namespaces of the app that reach outside the store: listeners,
  timers, locks, channels, watches and localStorage. Each one is marked as
  runtime, as in (ns ^:runtime …), so that they stay few: the listeners,
  timers, locks and channels in dk.simongray.elektrolyt.runtime, and
  localStorage in dk.simongray.elektrolyt.local. The check reads the
  sources, without their strings and comments."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def runtime-pattern
  "What only a runtime namespace may use: the browser's own calls, and
  those of play-nice and reitit that listen to the page."
  (re-pattern (str/join "|" ["addEventListener" "setTimeout" "setInterval"
                             "requestIdleCallback" "BroadcastChannel"
                             "\\.-locks" "navigator\\.locks" "add-watch"
                             "localStorage" "rfe/start!" "media/watch!"
                             "session/attach!" "session/actions!"
                             "tabs/attach!" "controls/listen!"])))

;; The quantifiers of the strings are possessive and the loop unrolled,
;; since java.util.regex recurses once a character for (?:a|b)*, which a
;; long docstring overflows the stack with
(defn code
  "The source `s` without its strings and comments."
  [s]
  (-> s
      (str/replace #"\"[^\"\\]*+(?:\\.[^\"\\]*+)*+\"" "\"\"")
      (str/replace #";[^\n]*" "")))

(defn runtime?
  "Whether the source `s` marks its namespace as runtime."
  [s]
  (boolean (re-find #"\(ns\s+\^:runtime\s" s)))

(deftest the-check-reads-code
  (is (re-find runtime-pattern (code "(js/setTimeout f 0)")))
  (is (re-find runtime-pattern (code "(tabs/attach! element lock)")))
  (is (nil? (re-find runtime-pattern (code "(def x \"a BroadcastChannel\") ; add-watch")))
      "but not a string or a comment")
  (is (runtime? "(ns ^:runtime a.b\n  \"Doc.\")"))
  (is (not (runtime? "(ns a.b)"))))

(deftest only-runtime-namespaces-reach-outside-the-store
  (doseq [file  (file-seq (io/file "src"))
          :when (re-find #"\.clj[cs]?$" (.getName ^java.io.File file))
          :let  [s    (slurp file)
                 used (re-find runtime-pattern (code s))]
          :when used]
    (let [marked? (runtime? s)]
      (is marked? (str file " uses " used " but isn't marked as runtime")))))
