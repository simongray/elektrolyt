(ns dk.simongray.elektrolyt.show.subscription
  "Subscribing to a show and unsubscribing from it, as changes to the
  library and the state."
  (:require [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.announcement :as announcement]))

(defn subscribing
  "The `state` while the feed at `url` is fetched for a subscription."
  [state url]
  (update state :subscribing (fnil conj #{}) url))

(defn failed
  "The change once the subscription to the show `title` at `url` has
  failed with `error`, which it announces."
  [url title error]
  {:ui #(-> (update % :subscribing disj url)
            (assoc-in [:subscribe-errors url] error)
            (announcement/announced (str "Couldn't subscribe to " title)))})

(defn subscribed
  "The change once the feed at `url` is `fetched` for a subscription to the
  show `title`: the subscription, or the fetch's error when it failed.
  Either way, it announces the outcome."
  [url title {:keys [error] :as fetched}]
  (if error
    (failed url title error)
    {:operation [:library/subscribe url fetched]
     :ui        #(-> (update % :subscribing disj url)
                     (update :subscribe-errors dissoc url)
                     (announcement/announced (str "Subscribed to " title)))}))

(defn unsubscribed
  "The change once the listener unsubscribes from the show `title` at
  `url` in `state`, or nil when the library isn't subscribed to it."
  [state url title]
  (when-let [k (some-> (:library state) (show/subscribed-key url))]
    {:operation [:library/unsubscribe k]
     :ui        #(announcement/announced % (str "Unsubscribed from " title))}))
