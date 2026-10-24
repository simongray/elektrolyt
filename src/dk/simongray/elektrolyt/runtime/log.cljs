(ns ^:runtime dk.simongray.elektrolyt.runtime.log
  "Logging in a tab or its web worker with Telemere, including the errors
  and rejected promises that nothing else caught.

  Each event has an id and its data, e.g. :library/save-failed."
  (:require [taoensso.telemere :as tel]))

(defn uncaught-error
  "The error in `event`, an error or unhandledrejection event, or else an
  ex-info that holds what was thrown."
  [^js event]
  (let [x (if (= "unhandledrejection" (.-type event))
            (.-reason event)
            (.-error event))]
    (if (instance? js/Error x)
      x
      (ex-info "Something that isn't an error went uncaught"
               {:value x :message (.-message event)}))))

(defn uncaught!
  "Log the uncaught error in `event` as :app/uncaught."
  [event]
  (tel/error! {:id :app/uncaught} (uncaught-error event)))

;; No preventDefault, so the browser still reports the error and the
;; end-to-end tests still fail on it
(defn start!
  "Start logging in `global`, the global scope of a tab or a worker:
  errors and warnings alone in a release build, and whatever nothing else
  catches."
  [^js global]
  (when-not ^boolean goog/DEBUG
    (tel/set-min-level! :warn))
  (.addEventListener global "error" uncaught!)
  (.addEventListener global "unhandledrejection" uncaught!))
