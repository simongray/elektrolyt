(ns dk.simongray.elektrolyt.ui.subscription
  "The button that subscribes to a show, on each page that lists one.

  The state holds the feed URLs that are being subscribed to in
  :subscribing, and the errors of those that failed by URL in
  :subscribe-errors."
  (:require [dk.simongray.elektrolyt.search :as search]))

(defn status
  "Where the subscription to the feed at `url` stands in the `state`, with
  the `subscribed` URLs: :subscribed, :subscribing, :ready, or :waiting
  while the library is restored."
  [state subscribed url]
  (cond
    (nil? subscribed)                    :waiting
    (search/subscribed? subscribed url)  :subscribed
    (contains? (:subscribing state) url) :subscribing
    :else                                :ready))

;; WCAG 2.2, SC 2.5.3 Label in Name: the name that a screen reader hears
;; starts with the visible label, e.g. "Subscribe to Podcasting 2.0". The
;; button is aria-disabled rather than disabled, so that it keeps the focus
;; as it changes. Subscribe is filled, as the main action of a show that
;; the listener doesn't have, as Play is of an episode.
(defn button
  "The button that subscribes to the show `title` at `url`, at the
  subscription `status`, or unsubscribes from it once it's subscribed,
  where the page offers that, `unsubscribe?`."
  ([status url title]
   (button status url title false))
  ([status url title unsubscribe?]
   (if (and unsubscribe? (= :subscribed status))
     [:button {:type "button" :on {:click [[:library/unsubscribe url title]]}}
      "Unsubscribe" [:span.visually-hidden " from " title]]
     [:button (cond-> {:type "button"}
                (not= :subscribed status) (assoc :class "primary")
                (not= :ready status) (assoc :aria-disabled "true")
                (= :ready status)    (assoc :on {:click [[:library/subscribe url title]]}))
      (case status
        :subscribed  "Subscribed"
        :subscribing "Subscribing…"
        "Subscribe")
      [:span.visually-hidden " to " title]])))

(defn error
  "The paragraph that tells why the subscription to the feed at `url`
  failed in the `state`, or nil."
  [state url]
  (when-let [e (get-in state [:subscribe-errors url])]
    [:p "Couldn't subscribe: " (:message e)]))
