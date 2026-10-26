(ns dk.simongray.elektrolyt.ui.home
  "Home's sections: the start of the queue, picks like what the listener
  heard last, and About.

  Apple Podcasts, Pocket Casts and Overcast don't show the queue on their
  home pages, though Apple's widget shows Up Next. Apple Podcasts' Home
  has You Might Like, and so does Pocket Casts' Discover.
  https://support.apple.com/guide/iphone/find-podcasts-iph19bb8e705/ios
  https://support.pocketcasts.com/article/subscribing-to-podcasts/"
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.library.queue.ui :as queue-ui]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]))

(def queue-count
  "How many episodes from the start of the queue Home shows, as many as
  the picks beside them."
  (get-in picks/places [:home :limit]))

(defn up-next
  "The section with the start of the queue in `state` and a link to the
  Queue page, or the text of an empty queue. The first episode is marked
  Now playing while the player has it."
  [{:keys [library] :as state}]
  (let [episodes (:episodes (queue/view library))
        playing  (:id (queue/current state))]
    [:section {:aria-labelledby "home-queue"}
     [:h2#home-queue {:tabindex -1} "Up next"]
     (if (empty? episodes)
       (queue-ui/empty-queue state)
       (list
        [:ol.queue {:role "list"}
         (for [{:keys [id] :as episode} (take queue-count episodes)]
           [:li {:replicant/key (pr-str id)}
            [:article.queue-episode
             (queue-ui/row state episode (when (= playing id) ["Now playing"]))]])]
        (lists-ui/more :route/queue "The whole queue" (queue-ui/summary episodes))))]))

(def picks-section
  "Home's section of picks: the id and text of its heading, the start of
  its line of why, and its text when there are none."
  {:id      "home-picks"
   :heading "You might like"
   :lead    "Like what you heard last, about"
   :none    "Episodes like the ones you listen to will show here."})

(def about
  "The section about the app, whose text is still to be written."
  [:section {:aria-labelledby "about"}
   [:h2#about {:tabindex -1} "About"]
   [:p "To be written."]])

(defn content
  "The content of Home in `state`: the queue, the picks and then About, or
  About first for a listener with no shows yet."
  [{:keys [library] :as state}]
  (let [lists (when library
                [:div.home-lists
                 (up-next state)
                 (picks-ui/section state picks-section (picks/home state))])]
    ;; the subscriptions alone, since the Library page's shows count every
    ;; episode, and the profile changes at each position report
    (if (empty? (some-> library :profile profile/subscriptions))
      (list about lists)
      (list lists about))))
