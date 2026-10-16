(ns user
  "The development environment in one JVM: `(start!)` runs the app's server
  and the shadow-cljs watch of the app and its views.

  Every JVM with dev/ on its classpath loads this namespace, including the
  shadow-cljs CLI, which runs without the :server alias. So this namespace
  resolves the server's functions only when they're called."
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow.server]))

(defn watch!
  "Start the shadow-cljs watch of the :app and :portfolio builds in this
  JVM."
  []
  ;; shadow-cljs refuses to run twice in one project, so a watch started
  ;; outside this JVM is left where it is and its complaint returned
  (try
    (shadow.server/start!)
    (shadow/watch :app)
    (shadow/watch :portfolio)
    (catch Exception e
      (ex-message e))))

(def secrets
  "The file of secrets beside the checkouts, whose :podcastindex holds the
  credentials of the Podcast Index, unless PODCAST_APP_SECRETS names
  another."
  (or (System/getenv "PODCAST_APP_SECRETS") "../secrets.edn"))

(defn start!
  "Start the server, with the credentials in secrets, and the watch. Both
  are no-ops when they're already running."
  []
  ((requiring-resolve 'dk.simongray.podcast-app.server/start!) {:secrets secrets})
  (watch!))

(defn stop!
  "Stop the server and the watch."
  []
  ((requiring-resolve 'dk.simongray.podcast-app.server/stop!))
  (shadow.server/stop!))

(defn restart!
  "Stop the server and start it again, leaving the watch as it is. Needed
  after reloading the server's namespace: the route table holds the
  handlers it was built with."
  []
  ((requiring-resolve 'dk.simongray.podcast-app.server/stop!))
  (start!))

(comment
  (start!)
  (restart!)
  (stop!)
  #_.)
