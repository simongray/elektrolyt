(ns user
  "The development environment in one JVM: the app's server and the
  shadow-cljs watch of the app and its views.

  Every JVM with dev/ on its classpath loads this namespace, including the
  shadow-cljs CLI, which runs without the :server alias, so the server's
  functions are resolved only when they're called."
  (:require [clojure.java.io :as io]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as shadow.server])
  (:import [java.io File]))

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

(def release-dir
  "Where a release of the app goes, as the :release of the :app build
  says, with the other files of public/ beside its scripts."
  "target/release")

(defn release!
  "Build a release of the app into release-dir, with a copy of the files
  of public/ but its scripts, so that the folder serves the app as public/
  does. It returns :done, as shadow-cljs's release does, and throws when
  the build fails."
  []
  (let [root (.toPath (io/file "public"))]
    (doseq [^File f (file-seq (io/file "public"))
            :let  [path (.relativize root (.toPath f))]
            :when (and (.isFile f) (not (.startsWith path "js")))]
      (io/make-parents (io/file release-dir (str path)))
      (io/copy f (io/file release-dir (str path)))))
  (shadow/release! :app)
  :done)

;; The library is kept in the browser by origin, so a release served on
;; another port would open with none
(defn listen!
  "Build a release of the app and serve it on the same origin, in place
  of the watch's build, which develop! serves again. A tab gets the
  release when it reloads."
  []
  (release!)
  ((requiring-resolve 'dk.simongray.elektrolyt.server/stop!))
  ((requiring-resolve 'dk.simongray.elektrolyt.server/start!)
   {:secrets secrets :client-dir release-dir :release? true}))

(defn develop!
  "Serve the watch's build again, after listen!."
  []
  (restart!))

(comment
  (start!)
  (restart!)
  (stop!)
  (listen!)
  (develop!)
  #_.)
