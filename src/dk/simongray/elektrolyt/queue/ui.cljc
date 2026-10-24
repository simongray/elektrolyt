(ns dk.simongray.elektrolyt.queue.ui
  "The views of the Queue page: the episode that the player has, and the
  episodes up next, which the listener plays, moves and removes.

  A move or a removal is announced in the page's status line, and the
  focus stays on the button that did it, or moves to the next row's after
  a removal, so that a keyboard and a screen reader keep their place."
  (:require [dk.simongray.elektrolyt.inbox :as inbox]
            [dk.simongray.elektrolyt.picks :as picks]
            [dk.simongray.elektrolyt.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.queue :as queue]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.text :as text]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn control-id
  "The id of the button `kind`, e.g. :remove, of the row of the episode
  `id`, by which the focus finds it again after a change."
  [id kind]
  (rows/control-id "queue" id kind))

(defn time-left
  "How long the `episode` of the queue has left, e.g. \"45 min left\", or
  its duration while it hasn't started."
  [{:keys [position remaining] :as episode}]
  (when (number? remaining)
    (cond-> (time-ui/duration remaining)
      (pos? position) (conj " left"))))

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
  "The `episode` of the queue, its artwork, title, show, time left and the
  feed it came from when that isn't its show's own, in the `state`, with
  the `marks` before its show, e.g. \"Now playing\"."
  ([state episode]
   (row state episode nil))
  ([state {:keys [show supplement] :as episode} marks]
   (lists-ui/row state episode :h3 (concat marks [show (time-left episode)
                                                  (facts-ui/source supplement)]))))

(defn now-playing
  "The section of the episode that the player has, of the `state`."
  [state episode]
  [:section {:aria-labelledby "now-playing"}
   [:h2#now-playing {:tabindex -1} "Now playing"]
   [:article.queue-episode (row state episode)]])

(defn summary
  "The count and the time left of the `episodes`, e.g. \"4 episodes, 2h
  30m\"."
  [episodes]
  (let [left (reduce + 0 (keep :remaining episodes))]
    (str (text/counted (count episodes) "episode")
         (when (pos? left) (str ", " (time-ui/duration-text left))))))

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
   [:p (str "This takes " (text/counted n "episode")
            " out of the queue. The episode that plays stays.")]
   [:form {:method "dialog"}
    [:button {:value "cancel" :autofocus true} "Cancel"]
    [:button {:value "clear"} "Clear the queue"]]])

;; AntennaPod's empty queue tells of the new episodes in the inbox, with a
;; way there
;; https://github.com/AntennaPod/AntennaPod/blob/develop/ui/i18n/src/main/res/values/strings.xml
(defn empty-queue
  "What an empty queue says in the `state`: where to find episodes for
  it."
  [state]
  (let [new-ones (count (inbox/episodes state))]
    (list
     [:p "Nothing in the queue."]
     (when (pos? new-ones)
       [:p "Your " [:a {:href (routes/href :route/inbox)} "Inbox"]
        " has " (text/counted new-ones "episode") " to choose from."])
     [:p "Add episodes from their pages with Play next or Play last, "
      "or find shows with " [:a {:href (routes/href :route/search)} "Search"] "."])))

(defn up-next
  "The section of the episodes up next of the `state`, with the undo of
  the last change."
  [state]
  (let [episodes (queue/up-next state)
        n        (count episodes)]
    [:section {:aria-labelledby "up-next"}
     [:h2#up-next {:tabindex -1} "Up next"]
     (if (empty? episodes)
       (empty-queue state)
       [:p.queue-summary (summary episodes)])
     ;; Adrian Roselli, Defining 'Toast' Messages: an undo that stays until
     ;; the next change, rather than in a toast that times out. The focus
     ;; goes to the first episode that it puts back, since the button goes,
     ;; or to the heading once another tab left it nothing to put back.
     (when-let [{[first-id] :ids :keys [label]} (queue/offered state)]
       [:button {:type         "button"
                 :id           undo-id
                 :data-refocus "#up-next"
                 :on           {:click [[:queue/undo]
                                        [:page/focus (control-id first-id :remove)]]}}
        label])
     (when (seq episodes)
       (list
        [:ol.queue {:role "list"}
         (for [[i {:keys [id] :as episode}] (map-indexed vector episodes)]
           (let [next-one (rows/neighbour episodes i)]
             [:li {:replicant/key (pr-str id)}
              [:article.queue-episode
               (row state episode)
               (actions episode (inc i) n
                        (if next-one (control-id (:id next-one) :remove) "up-next"))]]))]
        [:button {:type "button" :on {:click [[:dialog/open clear-dialog-id]]}}
         "Clear the queue"]))
     ;; it stays in the page, so that it closes where it opened
     (clear-dialog n)]))

(def picks-section
  "The section of the picks at the end of the queue: its heading's id and
  text, and the lead of its line of why."
  {:id "after-queue" :heading "More like your queue" :lead "About"})

;; Apple Music's AutoPlay adds similar songs after the queue, and Apple
;; Podcasts' Continue Playing has more of a show below it, both apart from
;; the queue itself
;; https://support.apple.com/guide/iphone/queue-up-your-music-ipha4521ef7d/ios
;; https://support.apple.com/guide/iphone/watch-and-listen-to-podcasts-iph3a22707a5/ios
(defn content
  "The content of the Queue page of the `state`, below its heading: what
  plays, what's up next, and the picks like them."
  [state]
  (list
   (when-let [episode (queue/current state)]
     (now-playing state episode))
   (up-next state)
   (picks-ui/section state picks-section (picks/after-queue state))))
