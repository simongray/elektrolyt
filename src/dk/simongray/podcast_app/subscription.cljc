(ns dk.simongray.podcast-app.subscription
  "Subscribing to a show and unsubscribing from it in the state.

  The state holds the feed URLs that are being subscribed to in
  :subscribing, and the errors of those that failed by URL in
  :subscribe-errors."
  (:require [dk.simongray.podcast-app.show :as show]))

(defn subscribing
  "The `state` while the feed at `url` is fetched for a subscription."
  [state url]
  (update state :subscribing (fnil conj #{}) url))

(defn failed
  "The change once the subscription to the show `title` at `url` failed
  with the `error` of its fetch or of the subscription, which the
  announcement tells."
  [url title error]
  {:ui #(-> (update % :subscribing disj url)
            (assoc-in [:subscribe-errors url] error)
            (assoc :announcement (str "Couldn't subscribe to " title)))})

(defn subscribed
  "The change once the feed at `url` is `fetched` for a subscription to the
  show `title`: the subscription when the fetch has the feed, and the error
  of the fetch otherwise.

  Either way, the announcement says how it went."
  [url title {:keys [error] :as fetched}]
  (if error
    (failed url title error)
    {:operation [:library/subscribe url fetched]
     :ui        #(-> (update % :subscribing disj url)
                     (update :subscribe-errors dissoc url)
                     (assoc :announcement (str "Subscribed to " title)))}))

(defn unsubscribed
  "The change of the `state` once the listener unsubscribed from the show
  `title` at `url`, or nil when the library doesn't subscribe to it."
  [state url title]
  (when-let [k (some-> (:library state) (show/library-key nil url))]
    {:operation [:library/unsubscribe k]
     :ui        #(assoc % :announcement (str "Unsubscribed from " title))}))
