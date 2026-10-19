(ns dk.simongray.podcast-app.ui.queue
  "The views of the Queue page: the episode that the player has, and the
  episodes up next, which the listener plays, moves and removes.

  A move or a removal is announced in the page's status line, and the
  focus stays on the button that did it, or moves to the next row's after
  a removal, so that a keyboard and a screen reader keep their place."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.facts :as facts-ui]
            [dk.simongray.podcast-app.ui.rows :as rows]
            [dk.simongray.podcast-app.ui.show :as show-ui]))

(defn control-id
  "The id of the button `kind`, e.g. :remove, of the row of the episode
  `id`, by which the focus finds it again after a change."
  [id kind]
  (str "queue-" (guid/short-hash (pr-str id)) "-" (name kind)))

(defn episode-href
  "The path of the page of the queued episode `id` titled `title`, with the
  URL of its feed when the library keeps it for the queue alone."
  [lib [feed-guid item-guid :as id] title]
  (routes/episode-href feed-guid item-guid title
                       (when-not (profile/subscribed? (:profile lib) feed-guid)
                         (get-in lib [:feeds feed-guid :url]))))

(defn time-left
  "How long the `episode` of the queue has left, e.g. \"45 min left\", or
  its duration while it hasn't started."
  [{:keys [duration position remaining] :as episode}]
  (when (number? remaining)
    [:time {:datetime (str "PT" (long remaining) "S")}
     (show-ui/duration-text remaining)
     (when (pos? position) " left")]))

(defn actions
  "The buttons of the `episode` at `position` of Up next, of `n` episodes,
  where the `next-focus` is the id of the button that takes the focus
  after a removal."
  [{:keys [id title] :as episode} position n next-focus]
  [:div.queue-actions
   (rows/button "Play" title {:id    (control-id id :play)
                              :class "primary"
                              :on    {:click [[:player/play {:id id} nil]]}})
   (rows/move-buttons title position n
                      #(control-id id %)
                      #(vector :queue/move id %))
   (rows/button "Remove" title {:id (control-id id :remove)
                                :on {:click [[:queue/remove id]
                                             [:page/focus next-focus]]}})])

(defn row
  "The `episode` of the queue, its artwork, title, show and time left, in
  the `state`."
  [{:keys [library] :as state} {:keys [id title show] :as episode}]
  (list
   (artwork/img episode 48 (colors/placeholder state episode))
   [:h3 [:a {:href (episode-href library id title)} title]]
   [:p show
    (when-let [left (time-left episode)]
      (list facts-ui/separator left))]))

(defn now-playing
  "The section of the episode that the player has, of the `state`."
  [state episode]
  [:section {:aria-labelledby "now-playing"}
   [:h2#now-playing "Now playing"]
   [:article.queue-episode (row state episode)]])

(defn summary
  "The count and the time left of the `episodes`, e.g. \"4 episodes, 2 h 30
  min\"."
  [episodes]
  (let [n    (count episodes)
        left (reduce + 0 (keep :remaining episodes))]
    (str n (if (= 1 n) " episode" " episodes")
         (when (pos? left) (str ", " (show-ui/duration-text left))))))

(def clear-dialog-id
  "The id of the dialog that asks before the queue is cleared."
  "clear-queue")

(def undo-id
  "The id of the button that undoes the last change of the queue."
  "queue-undo")

;; A native dialog, opened with showModal: it keeps the focus inside, closes
;; at Escape, and gives the focus back to the button that opened it. Cancel
;; has the focus first, since clearing is the change that loses more. The
;; queue is cleared once the dialog has closed with "clear", since the rest
;; of the page is inert while it's open, and the focus then goes to Undo.
;; https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/
(defn clear-dialog
  "The dialog that asks before the `n` episodes of Up next leave the
  queue."
  [n]
  [:dialog {:id              clear-dialog-id
            :aria-labelledby "clear-queue-title"
            :on              {:close [[:queue/clear-closed undo-id]]}}
   [:h2#clear-queue-title "Clear the queue?"]
   [:p (str "This takes " n (if (= 1 n) " episode" " episodes")
            " out of the queue. The episode that plays stays.")]
   [:form {:method "dialog"}
    [:button {:value "cancel" :autofocus true} "Cancel"]
    [:button {:value "clear"} "Clear the queue"]]])

(defn up-next
  "The section of the episodes up next of the `state`, with the undo of
  the last change."
  [{:keys [queue-undo] :as state}]
  (let [episodes (queue/up-next state)
        n        (count episodes)]
    [:section {:aria-labelledby "up-next"}
     [:h2#up-next {:tabindex -1} "Up next"]
     (if (empty? episodes)
       ;; AntennaPod's empty queue tells of the new episodes in the inbox,
       ;; with a way there
       ;; https://github.com/AntennaPod/AntennaPod/blob/develop/ui/i18n/src/main/res/values/strings.xml
       (let [new-ones (count (inbox/episodes state))]
         (list
          [:p "Nothing in the queue."]
          (when (pos? new-ones)
            [:p "Your " [:a {:href (routes/href :route/inbox)} "Inbox"]
             " has " (inbox/counted new-ones) " to choose from."])
          [:p "Add episodes from their pages with Play next or Add to queue, "
           "or find shows with " [:a {:href (routes/href :route/search)} "Search"] "."]))
       [:p.queue-summary (summary episodes)])
     ;; Adrian Roselli, Defining 'Toast' Messages: an undo that stays until
     ;; the next change, rather than in a toast that times out
     ;; the focus goes to the first episode that it puts back, since the
     ;; button goes
     (when queue-undo
       (let [[[first-id]] (:restore queue-undo)]
         [:button {:type "button"
                   :id   undo-id
                   :on   {:click [[:queue/undo]
                                  [:page/focus (control-id first-id :remove)]]}}
          (:label queue-undo)]))
     (when (seq episodes)
       (list
        [:ol.queue {:role "list"}
         (for [[i {:keys [id] :as episode}] (map-indexed vector episodes)]
           (let [next-one (or (get episodes (inc i)) (get episodes (dec i)))]
             [:li {:replicant/key (pr-str id)}
              [:article.queue-episode
               (row state episode)
               (actions episode (inc i) n
                        (if next-one (control-id (:id next-one) :remove) "up-next"))]]))]
        [:button {:type "button" :on {:click [[:dialog/open clear-dialog-id]]}}
         "Clear the queue"]))
     ;; it stays in the page, so that it closes where it opened
     (clear-dialog n)]))

(defn content
  "The content of the Queue page of the `state`, below its heading."
  [state]
  (if-not (:library state)
    [:p "The library couldn't be read."]
    (list
     (when-let [episode (queue/current state)]
       (now-playing state episode))
     (up-next state))))
