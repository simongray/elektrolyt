(ns dk.simongray.elektrolyt.server.timing
  "The Server-Timing of the server's responses, which says how long a
  request waited for its turn and for the work behind it.

  DevTools shows it beside each request, and the page and its worker read
  it as the serverTiming of the request's PerformanceResourceTiming.
  https://www.w3.org/TR/server-timing/"
  (:require [clojure.string :as str])
  (:import [java.util Locale]))

(def ^:dynamic *metrics*
  "A volatile of the metrics of the response being made, a map of name to
  milliseconds, or nil outside one."
  nil)

(defn timed
  "What `f` returns, with the milliseconds it took added to the `metric`
  of the response being made."
  [metric f]
  (let [start (System/nanoTime)]
    (try
      (f)
      (finally
        (when *metrics*
          (vswap! *metrics* update metric (fnil + 0.0)
                  (/ (- (System/nanoTime) start) 1e6)))))))

(defn header
  "The Server-Timing header of the `metrics`, a map of name to
  milliseconds."
  [metrics]
  (str/join ", " (for [[metric ms] metrics]
                   (String/format Locale/ROOT "%s;dur=%.1f"
                                  (object-array [metric ms])))))

(defn with-timing
  "The Ring `handler` whose responses have a Server-Timing of the metrics
  that timed adds while it answers."
  [handler]
  (fn [request]
    (binding [*metrics* (volatile! (array-map))]
      (let [response (handler request)
            metrics  @*metrics*]
        (cond-> response
          (seq metrics) (assoc-in [:headers "Server-Timing"] (header metrics)))))))
