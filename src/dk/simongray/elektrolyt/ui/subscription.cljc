(ns dk.simongray.elektrolyt.ui.subscription
  "The button that subscribes to a show, and the error of a subscription
  that failed."
  (:require [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

(defn status
  "Where the subscription to the feed at `url` stands in `state`, given the
  `subscribed` URLs: :subscribed, :subscribing, :ready, or :waiting until
  the library is restored."
  [state subscribed url]
  (cond
    (nil? subscribed)                    :waiting
    (search/subscribed? subscribed url)  :subscribed
    (contains? (:subscribing state) url) :subscribing
    :else                                :ready))

;; WCAG 2.2, SC 2.5.3 Label in Name: the accessible name starts with the
;; visible label, e.g. "Subscribe to Podcasting 2.0". The button is
;; aria-disabled rather than disabled, so that it keeps the focus as it
;; changes. Subscribe is the main action for a show that the listener
;; doesn't have, as Play is for an episode, so it's filled. Its icon is a
;; plus, as in Pocket Casts, then a check once subscribed, or a minus to
;; unsubscribe.
;; https://support.pocketcasts.com/article/subscribing-to-podcasts/
(defn button
  "The button that subscribes to the show `title` at `url`, labelled by the
  subscription `status`, or unsubscribes once it's subscribed if
  `unsubscribe?`."
  ([status url title]
   (button status url title false))
  ([status url title unsubscribe?]
   (if (and unsubscribe? (= :subscribed status))
     [controls/button {:icon icons/minus
                       :on   {:click (controls/single-click [[:library/unsubscribe url title]])}}
      "Unsubscribe" [:span.visually-hidden " from " title]]
     [controls/button (cond-> {:icon (if (= :subscribed status) icons/check icons/plus)}
                        (not= :subscribed status) (assoc :class "primary")
                        (not= :ready status) (assoc :aria-disabled "true")
                        (= :ready status)    (assoc :on {:click (controls/single-click
                                                                 [[:library/subscribe url title]])}))
      (case status
        :subscribed  "Subscribed"
        :subscribing "Subscribing…"
        "Subscribe")
      [:span.visually-hidden " to " title]])))

(defn error
  "The paragraph that says why the subscription to the feed at `url`
  failed in `state`, or nil."
  [state url]
  (when-let [e (get-in state [:subscribe-errors url])]
    [:p "Couldn't subscribe: " (:message e)]))
