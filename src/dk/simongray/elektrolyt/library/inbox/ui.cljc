(ns dk.simongray.elektrolyt.library.inbox.ui
  "The views of the inbox, on its own page and on the Library page: the new
  episodes of the listener's shows to play, queue or dismiss.

  Each action takes the episode out of the inbox, so its row goes and the
  focus moves to the same button in the next row, or else the row before,
  so that keyboard and screen reader users keep their place."
  (:require [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.queue.ui :as queue-ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui.text :as text]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn control-id
  "The id of the button `kind`, e.g. :dismiss, in the row of the episode
  `id`, for moving the focus back to it after a change."
  [id kind]
  (rows/control-id "inbox" id kind))

(def undo-id
  "The id of the button that undoes the last dismissal."
  "inbox-undo")

(defn facts
  "The facts of the inbox's `episode`: its show, its date, its length or
  time left, and the feed it came from when that isn't its show's own."
  [{:keys [show published supplement] :as episode}]
  [show (some-> published time-ui/date) (queue-ui/time-left episode)
   (facts-ui/source supplement)])

(defn row
  "The row of the inbox's `episode` in `state`, with its artwork, title and
  facts, and the title in a heading of `tag`, e.g. :h2."
  [state episode tag]
  (lists-ui/row state episode tag (facts episode)))

;; Castro's inbox actions: play, queue at the top or the bottom, and
;; archive. Each is a button that names its episode rather than a swipe,
;; which would need such a button anyway (WCAG 2.2, SC 2.5.1 Pointer
;; Gestures).
;; https://sixcolors.com/post/2016/08/castro-2-inbox-triage-for-podcast-episodes/
;; https://inclusive-components.design/a-todo-list/
;;
;; The icons match the episode page's, and Dismiss has the x of a close
;; button. The next row moves up under the pointer once a row has gone, so
;; a double click acts only once.
(defn actions
  "The buttons of the inbox's `episode`, where `focus` takes a button's
  kind and returns the action that moves the focus after it."
  [{:keys [id title] :as episode} focus]
  [:div.inbox-actions
   (rows/button "Play" title
                {:id    (control-id id :play)
                 :icon  icons/play
                 :class "primary"
                 :on    {:click (controls/single-click [[:player/play {:id id} nil] (focus :play)])}})
   (rows/button "Play last" title
                {:id   (control-id id :queue)
                 :icon icons/rows-plus-bottom
                 :on   {:click (controls/single-click [[:queue/add {:id id} title false] (focus :queue)])}})
   (rows/button "Play next" title
                {:id   (control-id id :next)
                 :icon icons/rows-plus-top
                 :on   {:click (controls/single-click [[:queue/add {:id id} title true] (focus :next)])}})
   (rows/button "Dismiss" title
                {:id   (control-id id :dismiss)
                 :icon icons/x
                 :on   {:click (controls/single-click [[:inbox/dismiss id] (focus :dismiss)])}})])

(defn focus-after
  "A function from a button's kind to the action that moves the focus once
  its row has gone: to the same button of `neighbour`, the episode whose
  row takes its place, or else to Undo after a dismissal, or the heading."
  [neighbour]
  (fn [kind]
    (cond
      neighbour         [:page/focus (control-id (:id neighbour) kind)]
      (= :dismiss kind) [:page/focus undo-id]
      :else             [:page/focus-heading])))

;; Adrian Roselli, Defining 'Toast' Messages: an undo that stays until the
;; next change, rather than a toast that times out. Dismiss all needs no
;; confirmation dialog, since it can be undone (NN/g, Confirmation
;; Dialogs).
;; https://adrianroselli.com/2020/01/defining-toast-messages.html
;; https://www.nngroup.com/articles/confirmation-dialog/
(defn tools
  "The buttons that act on all the inbox's `episodes`, and the button of
  the offered `undo`, which gives the focus to the heading at the selector
  `refocus` when another tab left nothing to put back."
  [episodes undo refocus]
  (when (or (seq episodes) undo)
    [:div.inbox-tools
     (when (seq episodes)
       (list
        [:button {:type "button"
                  :on   {:click [[:inbox/queue-all] [:page/focus-heading]]}}
         "Add all to queue"]
        [:button {:type "button"
                  :on   {:click [[:inbox/dismiss-all] [:page/focus undo-id]]}}
         "Dismiss all"]))
     ;; the focus goes to the first episode that it puts back, since the
     ;; button goes
     (when undo
       [:button {:type         "button"
                 :id           undo-id
                 :data-refocus refocus
                 :on           {:click [[:inbox/undo]
                                        [:page/focus (control-id (first (:ids undo)) :dismiss)]]}}
        (:label undo)])]))

;; NN/g, empty states: say why it's empty, and how it fills
;; https://www.nngroup.com/articles/empty-state-interface-design/
(def empty-inbox
  "The text of an empty inbox."
  (list
   [:p "No new episodes."]
   [:p "The new episodes of the shows in your "
    [:a {:href (routes/href :route/library)} "Library"] " arrive here."]))

(defn episode-list
  "The list of the inbox's `episodes` in `state`, or of the first `n`, with
  their buttons and each title in a heading of `tag`. After an action, the
  focus goes to the row that takes its row's place, which may be one that
  wasn't shown."
  ([state episodes tag]
   (episode-list state episodes tag nil))
  ([state episodes tag n]
   [:ul.inbox {:role "list"}
    (for [[i {:keys [id] :as episode}] (map-indexed vector (cond->> episodes n (take n)))]
      (let [neighbour (rows/neighbour episodes i)]
        [:li {:replicant/key (pr-str id)}
         [:article.inbox-episode
          (row state episode tag)
          (actions episode (focus-after neighbour))]]))]))

(defn content
  "The content of the Inbox page in `state`, below its heading."
  [state]
  (let [episodes (inbox/episodes state)
        n        (count episodes)]
    (list
     (if (zero? n)
       empty-inbox
       [:p (text/counted n "new episode")])
     (tools episodes (inbox/offered state) "main h1")
     (when (seq episodes)
       (episode-list state episodes :h2)))))

(def newest-count
  "How many of the newest episodes of the inbox the Library page shows."
  3)

;; The start of the inbox, as Home has the start of the queue. The buttons
;; that act on all of them stay on the Inbox page.
(defn section
  "The inbox section of the Library page in `state`: the newest episodes
  with their buttons, the undo of the last dismissal, and a link to the
  Inbox page."
  [state]
  (let [episodes (inbox/episodes state)
        n        (count episodes)]
    [:section {:aria-labelledby "inbox"}
     [:h2#inbox {:tabindex -1} "Inbox"]
     (tools nil (inbox/offered state) "#inbox")
     (if (zero? n)
       [:p "No new episodes."]
       (list
        (episode-list state episodes :h3 newest-count)
        (lists-ui/more :route/inbox "The whole Inbox" (text/counted n "new episode"))))]))
