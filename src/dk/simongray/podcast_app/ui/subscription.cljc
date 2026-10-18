(ns dk.simongray.podcast-app.ui.subscription
  "The button that subscribes to a show, on each page that lists one.

  The state holds the feed URLs that are being subscribed to in
  :subscribing, and the errors of those that failed by URL in
  :subscribe-errors."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.search :as search]))

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
;; as it changes.
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
                (not= :ready status) (assoc :aria-disabled "true")
                (= :ready status)    (assoc :on {:click [[:library/subscribe url title]]}))
      (case status
        :subscribed  "Subscribed"
        :subscribing "Subscribing…"
        "Subscribe")
      [:span.visually-hidden " to " title]])))

(def new-episodes-options
  "Where the new episodes of a show can go, as profile/new-episodes names
  them, with the text of each, in the order of the select."
  [[:inbox "The inbox"]
   [:queue "The end of the queue"]
   [:next  "Next in the queue"]
   [:none  "Nowhere"]])

;; Castro and AntennaPod: a choice for each show of where its new episodes
;; go, the inbox, the queue or nothing, set on the show's page. It shows as
;; the show is subscribed to, set to the default, rather than ask in a
;; dialog, which would stand in the way of every subscription.
;; https://castro.fm/support/auto-queue-and-auto-archive
;; https://github.com/AntennaPod/AntennaPod/blob/develop/app/src/main/res/xml/feed_settings.xml
;; A change applies at once, and moves neither the focus nor the page, so
;; it changes no context (WCAG 2.2, SC 3.2.2 On Input).
(defn new-episodes
  "The select of where the new episodes of the show `k` of the `library`
  go, for the page of a show that the listener subscribes to."
  [library k]
  (let [chosen (profile/new-episodes (profile/settings (:profile library) k))]
    [:div.new-episodes
     [:label {:for "new-episodes"} "New episodes go to"]
     [:select {:id "new-episodes"
               :on {:change [[:library/new-episodes k [:event/target-keyword]]]}}
      (for [[option text] new-episodes-options]
        [:option {:value (name option) :selected (= option chosen)} text])]]))

(defn error
  "The paragraph that tells why the subscription to the feed at `url`
  failed in the `state`, or nil."
  [state url]
  (when-let [e (get-in state [:subscribe-errors url])]
    [:p "Couldn't subscribe: " (:message e)]))
