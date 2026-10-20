(ns dk.simongray.podcast-app.refresh
  "Keeping the feeds of the library current, as podcast-clj's
  library/refresh! does it in parts: the page says what's due, the app's
  web worker fetches it, and the page applies what it found to the
  library. It runs as the app starts, whenever the library is due again
  while the page is visible, and when the listener asks.

  A refresh fetches only the feeds that are due, by each feed's own
  schedule, and those that answer 304 cost no body. A feed whose server
  can't answer 304 is checked by its start first, its :head-check?, so
  that only a feed with something new is fetched whole and parsed.

  One refresh runs at a time across the tabs, so a tab whose worker finds
  another refreshing reads what that one writes. A hidden tab doesn't
  refresh, so with every tab hidden, nothing does."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.fetching :as fetching]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.shows :as shows]))

(defonce ^{:doc "The timeout of the next refresh."}
  timer
  (atom nil))

(def max-wait-ms
  "The longest wait for the next refresh, after which the page asks the
  library again, e.g. after the computer slept."
  (* 60 60 1000))

(defn due?
  "Whether the library `lib` has feeds that are due now."
  [lib]
  (when-let [next-one (some-> lib library/next-refresh)]
    (<= (inst-ms next-one) (+ (js/Date.now) 1000))))

(declare refresh!)

(defn schedule!
  "Refresh the library of the `store` when it's due next, if the page is
  visible then."
  [store]
  (some-> @timer js/clearTimeout)
  (when-let [next-one (some-> (:library @store) library/next-refresh)]
    (let [wait (max 1000 (min max-wait-ms (- (inst-ms next-one) (js/Date.now))))]
      (reset! timer
              (js/setTimeout #(if (= "visible" js/document.visibilityState)
                                (refresh! store false)
                                (schedule! store))
                             wait)))))

(defn refreshed
  "The `state` once the refresh that gave the :refresh `result` is done,
  which the listener asked for when `asked?`."
  [state result asked?]
  (let [summary (shows/summary result asked?)]
    (cond-> (assoc state :refresh-status {:running? false
                                          :result   (select-keys result [:at :counts :added])})
      summary (assoc :announcement summary))))

;; WCAG 2.2, SC 4.1.3 Status Messages: one message as a refresh that the
;; listener asked for starts, and one with what it found, rather than one
;; for each feed, which would be too chatty
(defn refresh!
  "Refresh the library of the `store` in the worker: the feeds that are
  due, or, when the listener `asked?`, every feed of a subscription, and
  wait for the next. Nothing starts while a refresh is under way."
  [store asked?]
  (let [{:keys [library refresh-status]} @store
        online? (.-onLine js/navigator)]
    (when (and library @app-library/known (not (:running? refresh-status)))
      (some-> @timer js/clearTimeout)
      (swap! store #(cond-> (assoc % :refresh-status {:running? true :done 0})
                      asked? (assoc :announcement "Checking for new episodes…")))
      (-> (fetching/fetch-due! (library/due library
                                            (cond-> {:online? online?}
                                              asked? (assoc :feed-guids
                                                            (vec (keys (library/subscriptions library))))))
                               {:head-check? true :online? online?}
                               #(swap! store update :refresh-status merge %))
          (.then (fn [{:keys [found busy?]}]
                   (if busy?
                     (swap! store assoc :refresh-status {:running? false})
                     (let [lib (app-library/change! store {:operation [:library/refreshed found]})]
                       (swap! store refreshed (:refresh lib) asked?)
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
  (if (some-> (:library @store) due?)
    (refresh! store false)
    (schedule! store)))
