(ns dk.simongray.podcast-app.subscription
  "Subscribing to a show and unsubscribing from it in the state.

  The state holds the feed URLs that are being subscribed to in
  :subscribing, and the errors of those that failed by URL in
  :subscribe-errors."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.show :as show]))

(defn subscribing
  "The `state` while the feed at `url` is fetched for a subscription."
  [state url]
  (update state :subscribing (fnil conj #{}) url))

(defn subscribed
  "The `state` once the feed at `url` is `fetched` for a subscription to the
  show `title`: subscribed when the fetch has the feed, and with the error
  of the fetch or the subscription otherwise.

  Either way, the announcement says how it went."
  [state url title {:keys [error] :as fetched}]
  (let [[lib error] (if error
                      [nil error]
                      (try
                        [(library/subscribe (:library state) url fetched) nil]
                        (catch #?(:clj Exception :cljs :default) e
                          [nil {:message (ex-message e)}])))]
    (cond-> (update state :subscribing disj url)
      lib   (-> (assoc :library lib)
                (update :subscribe-errors dissoc url)
                (assoc :announcement (str "Subscribed to " title)))
      error (-> (assoc-in [:subscribe-errors url] error)
                (assoc :announcement (str "Couldn't subscribe to " title))))))

(defn unsubscribed
  "The `state` once the listener unsubscribed from the show `title` at
  `url`."
  [state url title]
  (if-let [k (some-> (:library state) (show/library-key nil url))]
    (-> state
        (update :library library/unsubscribe k)
        (assoc :announcement (str "Unsubscribed from " title)))
    state))
