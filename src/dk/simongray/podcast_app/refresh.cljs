(ns dk.simongray.podcast-app.refresh
  "Keeping the feeds of the library current with podcast-clj's
  library/refresh!: as the app starts, whenever the library is due again
  while the page is visible, and when the listener asks.

  A refresh fetches only the feeds that are due, by each feed's own
  schedule, and those that answer 304 cost no body. A feed whose server
  can't answer 304 is checked by its start first, its :head-check?, so
  that only a feed with something new is fetched whole and parsed.

  TODO: refresh in a web worker, as podcast-clj's guide describes it, with
  a Web Lock around each save and restore of the library, since a feed that
  changed is still parsed on the main thread, about a second for 20 MB."
  (:require [dk.simongray.podcast.app.library :as library]
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
  "The `state` once the refresh that gave the library `refreshed` is done,
  which the listener asked for when `asked?`."
  [state refreshed asked?]
  (let [result (:refresh refreshed)]
    (-> state
        (update :library library/merge refreshed)
        (assoc :refresh {:running? false
                         :result   (select-keys result [:at :counts :added])})
        (cond-> (shows/summary result asked?)
          (assoc :announcement (shows/summary result asked?))))))

;; WCAG 2.2, SC 4.1.3 Status Messages: one message as a refresh that the
;; listener asked for starts, and one with what it found, rather than one
;; for each feed, which would be too chatty
(defn refresh!
  "Refresh the library of the `store`: the feeds that are due, or, when
  the listener `asked?`, every feed of a subscription. Save it, and wait
  for the next. Nothing starts while a refresh is under way."
  [store asked?]
  (let [{:keys [library refresh]} @store]
    (when (and library (not (:running? refresh)))
      (some-> @timer js/clearTimeout)
      (swap! store #(cond-> (assoc % :refresh {:running? true :done 0})
                      asked? (assoc :announcement "Checking for new episodes…")))
      (-> (library/refresh! library
                            (cond-> {:head-check? true
                                     :online?     (.-onLine js/navigator)
                                     :on-feed-fn  #(swap! store update :refresh merge
                                                          (select-keys % [:done :total]))}
                              asked? (assoc :feed-guids (vec (keys (library/subscriptions library))))))
          (.then (fn [lib]
                   (swap! store refreshed lib asked?)
                   (app-library/save! (:library @store))))
          (.catch (fn [e]
                    (js/console.error "The library didn't refresh" e)
                    (swap! store assoc :refresh {:running? false})))
          (.finally #(schedule! store))))))

(defn refresh-if-due!
  "Refresh the library of the `store` when feeds are due, e.g. as the app
  starts or the page becomes visible, and wait for the next otherwise."
  [store]
  (if (some-> (:library @store) due?)
    (refresh! store false)
    (schedule! store)))
