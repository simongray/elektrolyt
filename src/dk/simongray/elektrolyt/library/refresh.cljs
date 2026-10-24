(ns dk.simongray.elektrolyt.library.refresh
  "Keeping the feeds of the library current: the page picks the feeds that
  are due, the web worker fetches them, and the page applies the result.

  A refresh runs as the app starts, whenever feeds are due while the page
  is visible, and when the listener asks. Only one tab refreshes at a
  time, and the others read what it writes.

  A feed of DR's own that was fetched before the listener turned the
  episodes of drpodcast.nu on or off is stale. It's fetched whole ahead of
  the feeds that are due, once per page load, so that one whose fetch
  fails waits for its schedule."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.library.shows :as shows]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]
            [taoensso.telemere :as tel]))

(def max-wait-ms
  "The longest wait before the next check for feeds that are due, since a
  timer pauses while the computer sleeps."
  (* 60 60 1000))

(defonce ^{:doc "The stale feeds that this page has refreshed, each as a
  pair of its key and whether the episodes of drpodcast.nu were on."}
  refreshed-stale
  (atom #{}))

(defn stale
  "The keys of the stale feeds in the library of `state` that this page
  hasn't refreshed yet."
  [{:keys [library] :as state}]
  (when library
    (let [on? (settings/alternative-feeds? state)]
      (remove #(contains? @refreshed-stale [% on?]) (drpodcast/stale library on?)))))

(defn next-one
  "When the library in `state` should refresh next, or nil when there's
  nothing to fetch. With stale feeds it's now, and after a refresh that
  found the device offline it's a while later."
  [{:keys [library refresh-status] :as state}]
  (if (seq (stale state))
    (js/Date.)
    (some-> library (library/next-refresh {:after (:result refresh-status)}))))

(defn due?
  "Whether the library in `state` has feeds that are due now."
  [state]
  (when-let [next-one (next-one state)]
    (<= (inst-ms next-one) (+ (js/Date.now) 1000))))

(declare refresh!)

(defn schedule!
  "Refresh the library in `store` when it's next due, if the page is
  visible by then."
  [store]
  (runtime/cancel! ::next)
  (when-let [next-one (next-one @store)]
    (let [wait (max 1000 (min max-wait-ms (- (inst-ms next-one) (js/Date.now))))]
      (runtime/later! ::next wait #(if (runtime/visible?)
                                     (refresh! store false)
                                     (schedule! store))))))

(defn started
  "The `state` as a refresh starts, announced when the listener `asked?`
  for it."
  [state asked?]
  (cond-> (assoc state :refresh-status {:running? true :done 0})
    asked? (assoc :announcement "Checking for new episodes…")))

(defn refreshed
  "The `state` once a refresh is done, with its `result` and a summary to
  announce if the listener `asked?` for it or it found something new."
  [state result asked?]
  (let [summary (shows/summary result asked?)]
    (cond-> (assoc state :refresh-status {:running? false
                                          :result   (dissoc result :library)})
      summary (assoc :announcement summary))))

(defn refreshed!
  "Apply the feeds that a refresh `found` to the library in `store` and
  save it, given whether the listener `asked?` for the refresh."
  [store found asked?]
  (let [result (app-library/change! store
                                    {:operation [:library/refreshed found]})]
    (swap! store refreshed result asked?)
    (app-library/save! store)))

;; WCAG 2.2, SC 4.1.3 Status Messages: a refresh that the listener asked
;; for announces its start and its result, rather than each feed
(defn refresh!
  "Refresh the library in `store` in the web worker, then schedule the
  next refresh. It fetches every subscribed feed when the listener
  `asked?`, or else the stale feeds, or else the feeds that are due.

  Stale feeds are fetched whole, since a check of their start would find
  nothing new. Nothing starts while a refresh is under way."
  [store asked?]
  (let [{:keys [library refresh-status] :as state} @store
        online? (.-onLine js/navigator)
        on?     (settings/alternative-feeds? state)
        whole   (vec (stale state))
        guids   (cond
                  asked?      (vec (keys (library/subscriptions library)))
                  (seq whole) whole)]
    (when (and library @app-library/known (not (:running? refresh-status)))
      (runtime/cancel! ::next)
      (swap! refreshed-stale into (map #(vector % on?)) whole)
      (swap! store started asked?)
      (-> (fetching/fetch-due! (library/due library (cond-> {:online? online?}
                                                      guids (assoc :feed-guids guids)))
                               {:head-check?        (empty? whole)
                                :online?            online?
                                :alternative-feeds? on?}
                               #(swap! store update :refresh-status merge %))
          (.then (fn [{:keys [found busy?]}]
                   (if busy?
                     (swap! store assoc :refresh-status {:running? false})
                     (refreshed! store found asked?))))
          (.catch (fn [e]
                    (tel/log! {:level :error :id :library/refresh-failed :error e}
                              "The library didn't refresh")
                    (swap! store assoc :refresh-status {:running? false})))
          (.finally #(schedule! store))))))

(defn refresh-if-due!
  "Refresh the library in `store` if feeds are due, or else schedule the
  next refresh."
  [store]
  (if (due? @store)
    (refresh! store false)
    (schedule! store)))
