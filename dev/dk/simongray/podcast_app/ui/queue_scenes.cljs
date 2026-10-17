(ns dk.simongray.podcast-app.ui.queue-scenes
  "Scenes of the Queue page: the episode that plays and those up next, the
  undo of a removal, and a queue with nothing in it."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(def url "https://feeds.podcastindex.org/pc20.xml")

(def feed
  {:rss/title "Podcasting 2.0"
   :rss/items (vec (for [[i title] [[275 "Episode 275: \"Off The Rails\""]
                                    [274 "Episode 274: \"Vibe Cloning\""]
                                    [273 "Episode 273: \"Advertising is the Horse and Buggies of Podcasting\""]
                                    [272 "Episode 272: \"Is this Y'all?\""]]]
                     {:rss/title       title
                      :rss/guid        {:content (str "PC20-" i)}
                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                      :itunes/duration (+ 5400 (* 60 (- i 270)))}))})

(def lib
  (library/subscribe (library/make "scenes") url feed))

(defn id [i] [(first (keys (:feeds lib))) (str "PC20-" i)])

(defn queued
  "The state of the Queue page with the episodes `is` queued in order, the
  first of them in the player."
  [& is]
  (let [lib (-> (reduce #(library/update-profile %1 profile.queue/enqueue (id %2)) lib is)
                (library/step [:progress (id (first is)) 630 5400]))]
    {:route   {:name :route/queue}
     :library lib
     :player  {:id       (id (first is))
               :playback (library/playback lib (id (first is)))
               :media    {:paused true :current-time 630 :duration 5400}}}))

(defscene the-queue
  "The episode that plays, and three up next, each with its buttons."
  (ui/page (queued 275 274 273 272)))

(defscene after-a-removal
  "The undo of the episode that the listener just removed, which stays
  until the next change."
  (ui/page (queue/removed (queued 275 274 273 272) (id 273))))

(defscene nothing-up-next
  "A queue with only the episode that plays."
  (ui/page (queued 275)))
