(ns user
  "The development environment in one JVM: the app's server and the
  shadow-cljs watch of the app and its views.

  Every JVM with dev/ on its classpath loads this namespace, including the
  shadow-cljs CLI, which runs without the :server alias, so the server's
  functions are resolved only when they're called."
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow.server]))

(defn watch!
  "Start the shadow-cljs watch of the :app and :portfolio builds in this
  JVM."
  []
  ;; shadow-cljs refuses to run twice in one project, so with a watch
  ;; outside this JVM, this returns the error message
  (try
    (shadow.server/start!)
    (shadow/watch :app)
    (shadow/watch :portfolio)
    (catch Exception e
      (ex-message e))))

(def secrets
  "The path of the secrets file: PODCAST_APP_SECRETS, or else the file
  beside the checkouts."
  (or (System/getenv "PODCAST_APP_SECRETS") "../secrets.edn"))

(defn start!
  "Start the server with the secrets file, and the watch. Both are no-ops
  when they're already running."
  []
  ((requiring-resolve 'dk.simongray.elektrolyt.server/start!) {:secrets secrets})
  (watch!))

(defn stop!
  "Stop the server and the watch."
  []
  ((requiring-resolve 'dk.simongray.elektrolyt.server/stop!))
  (shadow.server/stop!))

(defn restart!
  "Stop the server and start it again, leaving the watch as it is. The
  route table holds the handlers it was built with, so this is needed
  after reloading the server's namespace."
  []
  ((requiring-resolve 'dk.simongray.elektrolyt.server/stop!))
  (start!))

(comment
  (start!)
  (restart!)
  (stop!)
  #_.)
