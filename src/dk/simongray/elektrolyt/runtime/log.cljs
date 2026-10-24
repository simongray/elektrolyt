(ns ^:runtime dk.simongray.elektrolyt.runtime.log
  "The logs of the app in a tab or in its web worker, through Telemere:
  errors and warnings alone in a release build, and the errors and
  rejected promises that nothing else caught, as :app/uncaught.

  Each place that logs names its event by an id, with its data, e.g.
  :library/save-failed, as the error maps of the app and its libraries
  do."
  (:require [taoensso.telemere :as tel]))

(defn uncaught-error
  "The error that the `event` of an error or a rejected promise carries,
  or else one that holds what was thrown or rejected instead."
  [^js event]
  (let [x (if (= "unhandledrejection" (.-type event))
            (.-reason event)
            (.-error event))]
    (if (instance? js/Error x)
      x
      (ex-info "Something that isn't an error went uncaught"
               {:value x :message (.-message event)}))))

(defn uncaught!
  "Log the error of the `event` of an error or a rejected promise that
  nothing caught."
  [event]
  (tel/error! {:id :app/uncaught} (uncaught-error event)))

;; The browser still reports what nothing caught, since nothing prevents
;; it, so that the end-to-end tests, which fail on a page's errors, see it
(defn start!
  "Log in the global scope `global` of a tab or a worker: errors and
  warnings alone in a release build, and what nothing else catches."
  [^js global]
  (when-not ^boolean goog/DEBUG
    (tel/set-min-level! :warn))
  (.addEventListener global "error" uncaught!)
  (.addEventListener global "unhandledrejection" uncaught!))
