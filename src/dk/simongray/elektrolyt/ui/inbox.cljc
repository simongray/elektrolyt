(ns dk.simongray.elektrolyt.ui.inbox
  "The views of the Inbox page: the new episodes of the listener's shows,
  the newest first, which the listener plays, queues or dismisses, one at
  a time or all at once.

  Each of them takes an episode out of the inbox, so its row goes, and the
  focus moves to the same button of the next row, or of the row before
  the last, so that a keyboard and a screen reader keep their place. What
  happened is announced in the page's status line."
  (:require [dk.simongray.elektrolyt.inbox :as inbox]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.text :as text]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.queue :as queue-ui]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn control-id
  "The id of the button `kind`, e.g. :dismiss, of the row of the episode
  `id`, by which the focus finds it again after a change."
  [id kind]
  (rows/control-id "inbox" id kind))

(def undo-id
  "The id of the button that undoes the last dismissal."
  "inbox-undo")

(defn facts
  "The facts of the `episode` of the inbox: its show, when it came out, and
  how long it is, or has left once it has started."
  [{:keys [show published] :as episode}]
  [show (some-> published time-ui/date) (queue-ui/time-left episode)])

(defn row
  "The `episode` of the inbox, its artwork, title and facts, in the
  `state`, with its title in a heading of `tag`, e.g. :h2."
  [state episode tag]
  (lists-ui/row state episode tag (facts episode)))

;; Castro's actions on an episode of its inbox: play, queue at the top or
;; the bottom, and archive. Each is a button that names its episode,
;; rather than a swipe, which would need such a button anyway (WCAG 2.2,
;; SC 2.5.1 Pointer Gestures).
;; https://sixcolors.com/post/2016/08/castro-2-inbox-triage-for-podcast-episodes/
;; https://inclusive-components.design/a-todo-list/
(defn actions
  "The buttons of the `episode` of the inbox, where `focus` gives the
  action that moves the focus after one of them, by the kind of its
  button."
  [{:keys [id title] :as episode} focus]
  [:div.inbox-actions
   (rows/button "Play" title
                {:id    (control-id id :play)
                 :class "primary"
                 :on    {:click [[:player/play {:id id} nil] (focus :play)]}})
   (rows/button "Add to queue" title
                {:id (control-id id :queue)
                 :on {:click [[:queue/add {:id id} title false] (focus :queue)]}})
   (rows/button "Play next" title
                {:id (control-id id :next)
                 :on {:click [[:queue/add {:id id} title true] (focus :next)]}})
   (rows/button "Dismiss" title
                {:id (control-id id :dismiss)
                 :on {:click [[:inbox/dismiss id] (focus :dismiss)]}})])

(defn focus-after
  "A function of the kind of a button of the row before `neighbour`, the
  next row's episode or nil, which gives the action that moves the focus
  once the row has gone: to that button of the neighbour, or else to the
  undo of a dismissal, or the page's heading."
  [neighbour]
  (fn [kind]
    (cond
      neighbour         [:page/focus (control-id (:id neighbour) kind)]
      (= :dismiss kind) [:page/focus undo-id]
      :else             [:page/focus-heading])))

;; Adrian Roselli, Defining 'Toast' Messages: an undo that stays until the
;; next change, rather than in a toast that times out. Dismissing all
;; needs no dialog that asks first, since it can be undone (NN/g,
;; Confirmation Dialogs).
;; https://adrianroselli.com/2020/01/defining-toast-messages.html
;; https://www.nngroup.com/articles/confirmation-dialog/
(defn tools
  "The buttons that act on all the `episodes` of the inbox, and the one that
  undoes the last dismissal, the `undo` as inbox/offered has it, which
  gives the focus to the heading that the selector `refocus` names once
  another tab left it nothing to put back."
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
  "What the Inbox page says when it has no episodes."
  (list
   [:p "No new episodes."]
   [:p "The new episodes of the shows in your "
    [:a {:href (routes/href :route/library)} "Library"] " arrive here."]))

(defn episode-list
  "The list of the inbox's `episodes` in the `state`, or of the first `n`
  of them when given, with their actions, and each title in a heading of
  `tag`. After an action, the focus goes to the row that takes the place
  of its row, which may be one that wasn't shown."
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
  "The content of the Inbox page of the `state`, below its heading."
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

;; The start of the inbox, as Home has the start of the queue, with a link
;; to the whole of it, whose page has the actions on all of them
(defn section
  "The section of the Library page of the `state` with the newest episodes
  of the inbox and their actions, the undo of the last dismissal, and a
  link to the Inbox page."
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
