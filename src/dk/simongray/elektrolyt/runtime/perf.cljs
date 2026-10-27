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

;; What the page does while it's hidden, e.g. with the screen locked during
;; playback, is logged once it's shown, since a remote inspector that
;; watches can keep the page awake itself
(defonce ^{:doc "The tally of the page's work since it was hidden, or nil
  while it's shown: when it was hidden, as :since, and a count of each
  kind of work, e.g. :renders."}
  hidden-tally
  (atom nil))

(defn tallied!
  "Add `n`, or one, to `k` in the tally of the page's work while it's
  hidden."
  ([k]
   (tallied! k 1))
  ([k n]
   (when @hidden-tally
     (swap! hidden-tally update k (fnil + 0) n))))

(defn timeupdated!
  "Count a timeupdate event of the player's element while the page is
  hidden."
  [_]
  (tallied! :timeupdates))

(defn hidden!
  "Start a tally of the page's work while it's hidden, when the app times
  itself."
  []
  (when ^boolean on?
    (reset! hidden-tally {:since (js/Date.now)})))

(defn tally-summary
  "The `tally` of the page's work while it was hidden until `now`: the
  minutes it was hidden, and each count with its rate a minute."
  [{:keys [since] :as tally} now]
  (let [minutes (/ (- now since) 60000)
        tenths  #(/ (js/Math.round (* 10 %)) 10)]
    (into {:minutes (tenths minutes)}
          (for [[k n] (dissoc tally :since)]
            [k {:count (js/Math.round n) :per-minute (tenths (/ n minutes))}]))))

(defn shown!
  "End the tally of the page's work while it was hidden, and log it as
  :perf/hidden-tally."
  []
  (when-let [tally @hidden-tally]
    (reset! hidden-tally nil)
    (tel/log! {:level :info :id :perf/hidden-tally
               :data  (tally-summary tally (js/Date.now))}
              "The page's work while it was hidden")))

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
          (tallied! :renders)
          (tallied! :render-ms ms)
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

;; Event Timing and Long Animation Frames: the browser's own record of a
;; slow interaction and its phases, and in Chromium of a frame that a task
;; blocked, with the scripts that ran in it
;; https://web.dev/articles/inp
;; https://developer.chrome.com/docs/web-platform/long-animation-frames
(defonce ^{:doc "The 20 slowest interactions and longest frames that the
  page has logged, slowest first, for report."}
  worst
  (atom []))

(defn interaction
  "The phases of the slowest event `e` of an interaction, a map of the
  Event Timing entry's :name, :start-time, :duration, :processing-start
  and :processing-end, in whole milliseconds."
  [{:keys [name start-time duration processing-start processing-end]}]
  {:kind         :interaction
   :type         name
   :ms           (js/Math.round duration)
   :input-delay  (js/Math.round (- processing-start start-time))
   :processing   (js/Math.round (- processing-end processing-start))
   :presentation (js/Math.round (- (+ start-time duration) processing-end))})

(defn frame
  "What a long animation frame `f` tells, a map of the entry's :duration,
  its :blocking-duration and its :scripts, each a map of the script's
  :invoker, :source, :forced-style-and-layout and :duration, with the
  three that ran longest."
  [{:keys [duration blocking-duration scripts]}]
  {:kind     :frame
   :ms       (js/Math.round duration)
   :blocking (js/Math.round blocking-duration)
   :scripts  (->> scripts
                  (sort-by :duration >)
                  (take 3)
                  (mapv #(-> %
                             (select-keys [:invoker :source])
                             (assoc :ms (js/Math.round (:duration %))
                                    :forced-layout (js/Math.round (:forced-style-and-layout %))))))})

(defn kept!
  "Keep the `summary` of a slow interaction or a long frame among the
  worst."
  [summary]
  (swap! worst #(->> (conj % summary) (sort-by :ms >) (take 20) vec)))

(defn target
  "The element `el` of an event, named by its tag and id alone, since its
  label can hold a show's or an episode's title."
  [^js el]
  (when (and el (.-tagName el))
    (str (.toLowerCase (.-tagName el)) (when (seq (.-id el)) (str "#" (.-id el))))))

(defn event-fields
  "The fields of the Event Timing entry `e` that interaction reads."
  [^js e]
  {:name             (.-name e)
   :start-time       (.-startTime e)
   :duration         (.-duration e)
   :processing-start (.-processingStart e)
   :processing-end   (.-processingEnd e)})

(defn frame-fields
  "The fields of the long animation frame `f` that frame reads."
  [^js f]
  {:duration          (.-duration f)
   :blocking-duration (.-blockingDuration f)
   :scripts           (for [^js s (.-scripts f)]
                        {:invoker                 (.-invoker s)
                         :source                  (.-sourceURL s)
                         :forced-style-and-layout (.-forcedStyleAndLayoutDuration s)
                         :duration                (.-duration s)})})

(defn observe!
  "Call `f` with the entries of the `type` that the browser has made and
  makes from now on, a few at a time, with the `opts`, where the browser
  has them."
  [type opts f]
  (when (some #{type} (.-supportedEntryTypes js/PerformanceObserver))
    (.observe (js/PerformanceObserver. (fn [entries] (f (.getEntries entries))))
              (clj->js (assoc opts :type type :buffered true)))))

(defn slowest-events
  "The event of each interaction among the Event Timing `entries` whose
  handlers took longest, e.g. its click rather than its pointerdown."
  [entries]
  (->> entries
       (filter #(pos? (.-interactionId ^js %)))
       (group-by #(.-interactionId ^js %))
       (map (fn [[_ events]]
              (apply max-key #(- (.-processingEnd ^js %) (.-processingStart ^js %))
                     events)))))

(defn observed!
  "Log each slow interaction over 100 ms as :perf/slow-interaction, with
  the page's route that `route-fn` gives, and in Chromium each frame that
  a task blocked over 100 ms with no slow interaction in it as
  :perf/long-frame, when the app times itself."
  [route-fn]
  (when ^boolean on?
    (let [interactions (atom ())
          during?      (fn [start end]
                         (some (fn [[s e]] (and (< s end) (< start e))) @interactions))]
      (observe! "event" {:durationThreshold 16}
                (fn [entries]
                  (doseq [^js e (slowest-events entries)
                          :when (< 100 (.-duration e))]
                    (swap! interactions #(take 20 (conj % [(.-startTime e)
                                                           (+ (.-startTime e) (.-duration e))])))
                    (let [summary (assoc (interaction (event-fields e))
                                         :target (target (.-target e))
                                         :route  (route-fn))]
                      (kept! summary)
                      (tel/log! {:level :info :id :perf/slow-interaction :data summary}
                                "An interaction was slow")))))
      (observe! "long-animation-frame" {}
                (fn [entries]
                  (doseq [^js f entries
                          :when (and (< 100 (.-blockingDuration f))
                                     (not (during? (.-startTime f)
                                                   (+ (.-startTime f) (.-duration f)))))]
                    (let [summary (assoc (frame (frame-fields f)) :route (route-fn))]
                      (kept! summary)
                      (tel/log! {:level :info :id :perf/long-frame :data summary}
                                "A frame was blocked"))))))))

(defn ^:export report
  "The worst interactions and frames that the page has logged, as JSON to
  paste, e.g. from the console as
  dk.simongray.elektrolyt.runtime.perf.report()."
  []
  (js/JSON.stringify (clj->js @worst) nil 2))
