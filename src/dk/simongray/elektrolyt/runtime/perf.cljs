(ns ^:runtime dk.simongray.elektrolyt.runtime.perf
  "Timings of the app's work in the page and the worker, as User Timing
  measures: the renders, the library's operations, and the encoding and
  decoding of the worker's feeds. DevTools shows them on a track named
  Elektrolyt, and Firefox's profiler as UserTiming markers.

  They're on in the dev build and in a release built with enabled? set,
  e.g. to profile it, and gone from any other release. Each measure is
  cleared once it's made, since renders repeat, so a recording sees it but
  the page keeps none. A render over 16 ms is logged as
  :perf/slow-render.

  The stages of the page's start are marks under startup/ in every build,
  e.g. startup/library once the library is read, and the dev build logs
  them as :perf/startup once the first page paints."
  (:require [clojure.string :as str]
            [taoensso.telemere :as tel]))

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

;; The marks of the start stay in every build and are never cleared, so
;; that a test, or a profile of a release, finds them
(defn mark!
  "Mark `name` on the page's timeline at `at`, a time of performance.now,
  or now, and return the time."
  ([name]
   (mark! name (js/performance.now)))
  ([name at]
   (js/performance.mark name #js {:startTime at})
   at))

(defn startup-ms
  "The milliseconds from the navigation to each mark of the start, by its
  name after startup/, and to the first contentful paint, as :fcp, where
  the browser reports it."
  []
  (let [fcp (first (js/performance.getEntriesByName "first-contentful-paint"))]
    (cond-> (into {}
                  (for [m     (js/performance.getEntriesByType "mark")
                        :let  [n (.-name m)]
                        :when (str/starts-with? n "startup/")]
                    [(keyword (subs n (count "startup/"))) (js/Math.round (.-startTime m))]))
      fcp (assoc :fcp (js/Math.round (.-startTime fcp))))))

(defn started!
  "Log the start of the page as :perf/startup: the milliseconds to each of
  its marks, and how many `feeds` the library read."
  [feeds]
  (tel/log! {:level :info :id :perf/startup
             :data  (assoc (startup-ms) :feeds feeds)}
            "The page started"))

(defn changed-keys
  "The top-level keys whose values aren't identical in the maps `old` and
  `new`, in order."
  [old new]
  (->> (into (set (keys old)) (keys new))
       (remove #(identical? (get old %) (get new %)))
       sort
       vec))

(defonce ^{:doc "How many renders the page has made since its last frame,
  which more than one render a frame wastes, as :frame, and in all, as
  :all."}
  render-counts
  (atom {:frame 0 :all 0}))

(defn rendered!
  "Count a render, and return how many there were since the last frame."
  []
  (when (= 1 (:frame (swap! render-counts #(-> %
                                                (update :frame inc)
                                                (update :all inc)))))
    (js/requestAnimationFrame #(swap! render-counts assoc :frame 0)))
  (:frame @render-counts))

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

(defn action-keys
  "The keywords of the `actions`, never their arguments, which hold titles
  and URLs."
  [actions]
  (into [] (keep #(when (vector? %) (first %))) actions))

;; Nexus's action log, nexus.action-log/inspect with Dataspex, shows each
;; action and effect in full. This names the action behind a slow click,
;; and how many renders its effects made, but not the work that they leave
;; for a promise, and a dispatch from a render's on-mount counts in the
;; outer dispatch.
(def dispatch-interceptor
  "A Nexus interceptor that measures each dispatch of the page's own, e.g.
  of a click, as dispatch, and logs one that takes over 50 ms or renders
  more than once as :perf/slow-dispatch."
  {:id              ::dispatch
   :before-dispatch (fn [ctx]
                      (cond-> ctx
                        (empty? (:trace ctx))
                        (assoc ::start (js/performance.now)
                               ::renders (:all @render-counts))))
   :after-dispatch  (fn [{:keys [actions] ::keys [start] :as ctx}]
                      (when start
                        (let [props {:actions (action-keys actions)
                                     :renders (- (:all @render-counts) (::renders ctx))}
                              ms    (measure! "dispatch" props start)]
                          (when (or (< 50 ms) (< 1 (:renders props)))
                            (tel/log! {:level :info :id :perf/slow-dispatch
                                       :data  (assoc props :ms (js/Math.round ms))}
                                      "A dispatch took long or rendered more than once"))))
                      ctx)})
