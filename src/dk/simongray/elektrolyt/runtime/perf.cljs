(ns ^:runtime dk.simongray.elektrolyt.runtime.perf
  "Timings of the app's work in the page and the worker, as User Timing
  measures: the renders, the library's operations, and the encoding and
  decoding of the worker's feeds. DevTools shows them on a track named
  Elektrolyt, and Firefox's profiler as UserTiming markers.

  They're on in the dev build and in a release built with enabled? set,
  e.g. to profile it, and gone from any other release. Each measure is
  cleared once it's made, since renders repeat, so a recording sees it but
  the page keeps none. A render over 16 ms is logged as
  :perf/slow-render."
  (:require [taoensso.telemere :as tel]))

(goog-define ^{:doc "Whether a release times itself, which a release
  that's profiled sets in its :closure-defines."}
  enabled? false)

(def on?
  "Whether the app times itself."
  (or ^boolean goog/DEBUG ^boolean enabled?))

;; Chrome's extension of the Performance panel
;; https://developer.chrome.com/docs/devtools/performance/extension
(defn detail
  "The detail of a measure with the `props`, a map of keywords to
  keywords, strings or numbers, which DevTools shows on the track
  Elektrolyt."
  [props]
  #js {:devtools #js {:dataType   "track-entry"
                      :track      "Elektrolyt"
                      :properties (into-array (for [[k v] props]
                                                #js [(name k) (str v)]))}})

;; User Timing, section 3.1.3
;; https://w3c.github.io/user-timing/#measure-method
(defn measure!
  "Measure `name` with the `props` from `start`, a time of
  performance.now, until now, and return its milliseconds."
  [name props start]
  (let [end (js/performance.now)]
    (js/performance.measure name #js {:start start :end end :detail (detail props)})
    (js/performance.clearMeasures name)
    (- end start)))

(defn measured!
  "What `f` returns, measured as `name` with the `props` when the app
  times itself, and a promise until it settles."
  [name props f]
  (if ^boolean on?
    (let [start  (js/performance.now)
          result (f)]
      (if (instance? js/Promise result)
        (.then result #(measure! name props start) #(measure! name props start))
        (measure! name props start))
      result)
    (f)))

(defn changed-keys
  "The top-level keys whose values aren't identical in the maps `old` and
  `new`, in order."
  [old new]
  (->> (into (set (keys old)) (keys new))
       (remove #(identical? (get old %) (get new %)))
       sort
       vec))

(defonce ^{:doc "How many renders the page has made since its last frame,
  which more than one render a frame wastes."}
  renders
  (atom 0))

(defn rendered!
  "Count a render, and return how many there were since the last frame."
  []
  (when (= 1 (swap! renders inc))
    (js/requestAnimationFrame #(reset! renders 0)))
  @renders)

(defn timed-render
  "The watch `f` of the store that renders the page, measured as render
  with the page's route, the top-level keys that changed and the renders
  in its frame, when the app times itself."
  [f]
  (if-not ^boolean on?
    f
    (fn [old new]
      (if (identical? old new)
        (f old new)
        (let [start (js/performance.now)
              _     (f old new)
              props {:route   (get-in new [:route :name])
                     :changed (changed-keys old new)
                     :renders (rendered!)}
              ms    (measure! "render" props start)]
          (if (< 16 ms)
            (tel/log! {:level :info :id :perf/slow-render
                       :data  (assoc props :ms (js/Math.round ms))}
                      "A render took longer than a frame")
            (tel/log! {:level :debug :id :perf/render
                       :data  (assoc props :ms (js/Math.round ms))}
                      "A render")))))))
