(ns dk.simongray.elektrolyt.ui.home
  "The views of Home: the start of the queue, the picks like what the
  listener heard last, and About, which comes first for a listener who
  has no shows yet.

  Apple Podcasts, Pocket Casts and Overcast don't show the queue on their
  home pages, though Apple's widget shows Up Next. Apple Podcasts' Home
  has You Might Like, and Pocket Casts' Discover has it too, from what the
  listener heard.
  https://support.apple.com/guide/iphone/find-podcasts-iph19bb8e705/ios
  https://support.pocketcasts.com/article/subscribing-to-podcasts/"
  (:require [dk.simongray.elektrolyt.picks :as picks]
            [dk.simongray.elektrolyt.queue :as queue]
            [dk.simongray.elektrolyt.shows :as shows]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.picks :as picks-ui]
            [dk.simongray.elektrolyt.ui.queue :as queue-ui]))

(def queue-count
  "How many episodes of the queue Home shows: what plays and the next, or
  else the first, as many as the picks beside them."
  (get-in picks/places [:home :limit]))

(defn up-next
  "The section of the start of the queue of the `state`, marked Now
  playing while the player has its first episode, with a link to the
  Queue page, or what an empty queue says."
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
  "The section of Home's picks: its heading's id and text, the lead of
  its line of why, and what it says without picks."
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
  "The content of Home in the `state`, below its heading: About first for
  a listener who has no shows yet, and else last, after the queue and the
  picks, which sit side by side on a wide screen."
  [{:keys [library] :as state}]
  (let [lists (when library
                [:div.home-lists
                 (up-next state)
                 (picks-ui/section state picks-section (picks/home state))])]
    (if (empty? (some-> library shows/all-shows))
      (list about lists)
      (list lists about))))
