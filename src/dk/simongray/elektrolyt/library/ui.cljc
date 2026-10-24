(ns dk.simongray.elektrolyt.library.ui
  "The views of the Library page: the newest episodes in the inbox and the
  shows that the listener subscribes to."
  (:require [dk.simongray.elektrolyt.library.inbox.ui :as inbox-ui]
            [dk.simongray.elektrolyt.library.shows :as shows]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn choice
  "A select of the listener's choice of `k`, :sort or :layout, in `state`,
  labelled `label` and offering the `options`."
  [state k label options]
  (let [id (str "library-" (name k))]
    (list
     [:label {:for id} label]
     (controls-ui/select id options (shows/choice state k)
                         [:library/view k [:event/target-keyword]]))))

(defn refresh-line
  "The line that shows when the feeds were last `updated`, or how far the
  refresh in `state` has come, and the button that checks them all."
  [{:keys [refresh-status] :as state} updated]
  (let [{:keys [running? done total]} refresh-status]
    [:div.library-refresh
     [:p (cond
           running? (if total
                      (str "Checking " done " of " total " shows…")
                      "Checking for new episodes…")
           updated  (list "Updated " (time-ui/date updated))
           :else    "Not checked yet")]
     ;; aria-disabled rather than disabled, so that it keeps the focus
     [:button (cond-> {:type "button"}
                running?       (assoc :aria-disabled "true")
                (not running?) (assoc :on {:click [[:library/refresh]]}))
      "Check for new episodes"]]))

;; Inclusive Components, Cards: the title is the link rather than the whole
;; tile, whose every word would be the link's name
;; https://inclusive-components.design/cards/
(defn show-item
  "The tile of the library's `show` in `state`: its artwork and title,
  which link to its page, its counts and the date of its newest episode."
  [state {:keys [feed-guid title newest counts] :as show}]
  (let [href (routes/show-href feed-guid title)]
    [:li {:replicant/key feed-guid}
     [:article.library-show
      (artwork/linked href title (artwork/img show 160 (colors/placeholder state show)))
      [:h3 [:a {:href href} title]]
      (when-let [text (shows/counts-text counts)]
        [:p text])
      (when newest
        [:p "Newest episode " (time-ui/date newest)])]]))

(defn content
  "The content of the Library page for `state`, below its heading: the
  start of the inbox and the shows."
  [{:keys [library refresh-status] :as state}]
  (let [all (shows/all-shows library)]
    (if (empty? all)
      (list
       [:p "No shows yet."]
       [:p "Find shows to subscribe to with "
        [:a {:href (routes/href :route/search)} "Search"] "."])
      (list
       (inbox-ui/section state)
       [:section {:aria-labelledby "shows"}
        [:h2#shows {:tabindex -1} "Shows"]
        [:div.library-tools
         (choice state :sort "Sort by" shows/sorts)
         (choice state :layout "Show as" shows/layouts)]
        (refresh-line state (shows/updated-at library))
        ;; Safari drops the semantics of a list without markers
        [:ul {:role      "list"
              :class     ["library-shows" (name (shows/choice state :layout))]
              :aria-busy (str (boolean (:running? refresh-status)))}
         (map #(show-item state %) (shows/sorted all (shows/choice state :sort)))]]))))
