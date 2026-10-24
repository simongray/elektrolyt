(ns dk.simongray.elektrolyt.library.refresh
  "Keeping the feeds of the library current, as podcast-clj's
  library/refresh! does it in parts: the page says what's due, the app's
  web worker fetches it, and the page applies what it found to the
  library. It runs as the app starts, whenever the library is due again
  while the page is visible, and when the listener asks.

  A refresh fetches only the feeds that are due, by each feed's own
  schedule, and those that answer 304 cost no body. A feed whose server
  can't answer 304 is checked by its start first, its :head-check?, so
  that only a feed with something new is fetched whole and parsed.

  A feed of DR's own that was fetched before the listener turned the
  episodes of drpodcast.nu on or off is fetched whole at once, before the
  feeds that are due, and once a page, so that one whose fetch fails
  waits for its schedule.

  One refresh runs at a time across the tabs, so a tab whose worker finds
  another refreshing reads what that one writes. A hidden tab doesn't
  refresh, so with every tab hidden, nothing does."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.shows :as shows]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]))

(def max-wait-ms
  "The longest wait for the next refresh, after which the page asks the
  library again, e.g. after the computer slept."
  (* 60 60 1000))

(defonce ^{:doc "The stale feeds that this page refreshed, each a pair of
  its key and whether DR's own feeds took the episodes of drpodcast.nu
  then."}
  refreshed-stale
  (atom #{}))

(defn stale
  "The keys of the feeds of the library of the `state` that a fetch would
  give otherwise now, as drpodcast/stale has them, which this page hasn't
  refreshed yet."
  [{:keys [library] :as state}]
  (when library
    (let [on? (settings/alternative-feeds? state)]
      (remove #(contains? @refreshed-stale [% on?]) (drpodcast/stale library on?)))))

(defn next-one
  "When the library of the `state` should refresh next, after the last
  refresh, which waits a while after one that found the device offline, or
  nil when it has nothing to fetch or the state no library. With stale
  feeds, it's now."
  [{:keys [library refresh-status] :as state}]
  (if (seq (stale state))
    (js/Date.)
    (some-> library (library/next-refresh {:after (:result refresh-status)}))))

(defn due?
  "Whether the library of the `state` has feeds that are due now."
  [state]
  (when-let [next-one (next-one state)]
    (<= (inst-ms next-one) (+ (js/Date.now) 1000))))

(declare refresh!)

(defn schedule!
  "Refresh the library of the `store` when it's due next, if the page is
  visible then."
  [store]
  (runtime/cancel! ::next)
  (when-let [next-one (next-one @store)]
    (let [wait (max 1000 (min max-wait-ms (- (inst-ms next-one) (js/Date.now))))]
      (runtime/later! ::next wait #(if (runtime/visible?)
                                     (refresh! store false)
                                     (schedule! store))))))

(defn refreshed
  "The `state` once the refresh that gave the `result` beside the library
  is done, which the listener asked for when `asked?`."
  [state result asked?]
  (let [summary (shows/summary result asked?)]
    (cond-> (assoc state :refresh-status {:running? false
                                          :result   (dissoc result :library)})
      summary (assoc :announcement summary))))

;; WCAG 2.2, SC 4.1.3 Status Messages: one message as a refresh that the
;; listener asked for starts, and one with what it found, rather than one
;; for each feed, which would be too chatty
(defn refresh!
  "Refresh the library of the `store` in the worker: the stale feeds, or
  else the feeds that are due, or, when the listener `asked?`, every feed
  of a subscription, and wait for the next. Stale feeds are fetched whole,
  since their start can show nothing new. Nothing starts while a refresh
  is under way."
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
      (swap! store #(cond-> (assoc % :refresh-status {:running? true :done 0})
                      asked? (assoc :announcement "Checking for new episodes…")))
      (-> (fetching/fetch-due! (library/due library (cond-> {:online? online?}
                                                      guids (assoc :feed-guids guids)))
                               {:head-check?        (empty? whole)
                                :online?            online?
                                :alternative-feeds? on?}
                               #(swap! store update :refresh-status merge %))
          (.then (fn [{:keys [found busy?]}]
                   (if busy?
                     (swap! store assoc :refresh-status {:running? false})
                     (let [result (app-library/change! store {:operation [:library/refreshed found]})]
                       (swap! store refreshed result asked?)
                       (.then (app-library/save! store)
                              #(when (:error %)
                                 (js/console.error "The library wasn't saved" (clj->js %))))))))
          (.catch (fn [e]
                    (js/console.error "The library didn't refresh" e)
                    (swap! store assoc :refresh-status {:running? false})))
          (.finally #(schedule! store))))))

(defn refresh-if-due!
  "Refresh the library of the `store` when feeds are due, e.g. as the app
  starts or the page becomes visible, and wait for the next otherwise."
  [store]
  (if (due? @store)
    (refresh! store false)
    (schedule! store)))
