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

(def busy-wait-ms
  "How long a tab waits after finding another tab's worker refreshing."
  (* 30 1000))

(defn held-until
  "The time in milliseconds before which no refresh starts by itself, by
  the `refresh-status`: a while after finding another tab refreshing, and
  after a failure, from a minute to max-wait-ms, doubling with each
  failure in a row. Nil when nothing holds it back."
  [{:keys [busy-at failed-at failures]}]
  (cond
    failed-at (+ (inst-ms failed-at) (min max-wait-ms (* 60000 (js/Math.pow 2 (dec failures)))))
    busy-at   (+ (inst-ms busy-at) busy-wait-ms)))

(defn next-one
  "When the library in `state` should refresh next, or nil when there's
  nothing to fetch. With stale feeds it's now, and after a refresh that
  found the device offline, failed or found another tab refreshing, it's
  a while later."
  [{:keys [library refresh-status] :as state}]
  (let [due  (if (seq (stale state))
               (js/Date.)
               (some-> library (library/next-refresh {:after (:result refresh-status)})))
        held (held-until refresh-status)]
    (if (and due held (< (inst-ms due) held))
      (js/Date. held)
      due)))

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
  (cond-> (update state :refresh-status #(assoc (select-keys % [:failures]) :running? true :done 0))
    asked? (assoc :announcement "Checking for new episodes…")))

(defn busy
  "The `state` once another tab's worker turned out to be refreshing at
  the instant `at`, announced when the listener `asked?` for the refresh."
  [state asked? at]
  (cond-> (assoc state :refresh-status {:running? false :busy-at at})
    asked? (assoc :announcement "Another tab is checking for new episodes")))

(defn failed
  "The `state` once a refresh failed at the instant `at`, one more failure
  in a row, announced when the listener `asked?` for the refresh."
  [state asked? at]
  (cond-> (update state :refresh-status
                  (fn [{:keys [failures]}]
                    {:running? false :failed-at at :failures (inc (or failures 0))}))
    asked? (assoc :announcement "Couldn't check for new episodes")))

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

(defn fetched!
  "A promise of what the web worker finds for the library in `store`:
  every subscribed feed when the listener `asked?`, or else the stale
  feeds, or else the feeds that are due.

  Stale feeds are fetched whole, since a check of their start would find
  nothing new."
  [store asked?]
  (let [{:keys [library] :as state} @store
        online? (.-onLine js/navigator)
        on?     (settings/alternative-feeds? state)
        whole   (vec (stale state))
        guids   (cond
                  asked?      (vec (keys (library/subscriptions library)))
                  (seq whole) whole)]
    (swap! refreshed-stale into (map #(vector % on?)) whole)
    (fetching/fetch-due! (library/due library (cond-> {:online? online?}
                                                guids (assoc :feed-guids guids)))
                         {:head-check?        (empty? whole)
                          :online?            online?
                          :alternative-feeds? on?}
                         #(swap! store update :refresh-status merge %))))

;; WCAG 2.2, SC 4.1.3 Status Messages: a refresh that the listener asked
;; for announces its start and its result, rather than each feed
(defn refresh!
  "Refresh the library in `store` in the web worker, with every subscribed
  feed if the listener `asked?`, then schedule the next refresh. Nothing
  starts while a refresh is under way, and what's due goes by what the
  other tabs wrote too, e.g. a refresh of theirs."
  [store asked?]
  (let [{:keys [library refresh-status]} @store]
    (when (and library @app-library/known (not (:running? refresh-status)))
      (runtime/cancel! ::next)
      (swap! store started asked?)
      (-> (app-library/caught-up! store)
          (.catch (constantly nil))
          (.then #(fetched! store asked?))
          (.then (fn [{:keys [found busy?]}]
                   (if busy?
                     (swap! store busy asked? (js/Date.))
                     (refreshed! store found asked?))))
          (.catch (fn [e]
                    (tel/log! {:level :error :id :library/refresh-failed :error e}
                              "The library didn't refresh")
                    (swap! store failed asked? (js/Date.))))
          (.finally #(schedule! store))))))

(defn refresh-if-due!
  "Refresh the library in `store` if feeds are due, or else schedule the
  next refresh."
  [store]
  (if (due? @store)
    (refresh! store false)
    (schedule! store)))
