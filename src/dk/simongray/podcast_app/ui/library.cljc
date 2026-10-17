(ns dk.simongray.podcast-app.ui.library
  "The views of the Library page: the shows that the listener subscribes
  to, in the order and the layout of their choice, and the refresh of
  their feeds."
  (:require [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.shows :as shows]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.show :as show-ui]))

(defn choice
  "A select of the listener's choice of `k`, :sort or :layout, labelled
  `label`, from the `options` of shows, in the `state`."
  [state k label options]
  (let [id     (str "library-" (name k))
        chosen (shows/choice state k)]
    (list
     [:label {:for id} label]
     [:select {:id id :on {:change [[:library/view k [:event/target-keyword]]]}}
      (for [[option text] options]
        [:option {:value (name option) :selected (= option chosen)} text])])))

(defn refresh-line
  "When the feeds were last fetched, `updated`, and the button that checks
  them all, or how far the refresh of the `state` has come."
  [{:keys [refresh] :as state} updated]
  (let [{:keys [running? done total]} refresh]
    [:div.library-refresh
     [:p (cond
           running? (if total
                      (str "Checking " done " of " total " shows…")
                      "Checking for new episodes…")
           updated  (list "Updated "
                          [:time {:datetime (show-ui/date-attribute updated)}
                           (show-ui/date-text updated)])
           :else    "Not checked yet")]
     ;; aria-disabled rather than disabled, so that it keeps the focus
     [:button (cond-> {:type "button"}
                running?       (assoc :aria-disabled "true")
                (not running?) (assoc :on {:click [[:library/refresh]]}))
      "Check for new episodes"]]))

;; Inclusive Components, Cards: the title is the link, rather than the
;; whole tile, whose every word would be the link's name, and the artwork
;; beside it is decoration. The stylesheet stretches the link over the
;; tile, so that a click on the artwork follows it too. The list keeps
;; role="list", since Safari drops the semantics of a list without markers.
;; https://inclusive-components.design/cards/
(defn show-item
  "The `show` of the library: its artwork, its title, which links to its
  page, its counts, and when its newest episode came out."
  [{:keys [feed-guid title newest counts] :as show}]
  [:li {:replicant/key feed-guid}
   [:article.library-show
    (artwork/img show 160 {})
    [:h2 [:a {:href (routes/show-href feed-guid title)} title]]
    (when-let [text (shows/counts-text counts)]
      [:p text])
    (when newest
      [:p "Newest episode "
       [:time {:datetime (show-ui/date-attribute newest)} (show-ui/date-text newest)]])]])

(defn content
  "The content of the Library page of the `state`, below its heading."
  [{:keys [library refresh] :as state}]
  (if-not library
    [:p "The library couldn't be read."]
    (let [all (shows/all-shows library)]
      (if (empty? all)
        (list
         [:p "No shows yet."]
         [:p "Find shows to subscribe to with "
          [:a {:href (routes/href :route/search)} "Search"] "."])
        (list
         [:div.library-tools
          (choice state :sort "Sort by" shows/sorts)
          (choice state :layout "Show as" shows/layouts)]
         (refresh-line state (shows/updated-at library))
         [:ul {:role      "list"
               :class     ["library-shows" (name (shows/choice state :layout))]
               :aria-busy (str (boolean (:running? refresh)))}
          (map show-item (shows/sorted all (shows/choice state :sort)))])))))
