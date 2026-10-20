(ns dk.simongray.podcast-app.ui.queue-scenes
  "Scenes of the Queue page: the episode that plays and those up next, the
  undo of a removal, and a queue with nothing in it."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.operations :as operations]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.scenes :refer [id lib]]
            [portfolio.replicant :refer [defscene]]))

(defn queued
  "The state of the Queue page with the episodes `is` queued in order, the
  first of them in the player."
  [& is]
  (let [lib (-> (reduce #(library/update-profile %1 profile.queue/enqueue (id %2)) lib is)
                (library/step [:progress (id (first is)) 630 5400])
                :library)]
    {:route   {:name :route/queue}
     :library lib
     :player  {:id    (id (first is))
               :media {:paused true :current-time 630 :duration 5400}}}))

(defscene the-queue
  "The episode that plays, and three up next, each with its buttons."
  (ui/page (queued 275 274 273 272)))

(defscene after-a-removal
  "The undo of the episode that the listener just removed, which stays
  until the next change."
  (let [state (queued 275 274 273 272)]
    (ui/page (operations/changed state (queue/removed state (id 273))))))

(defscene nothing-up-next
  "A queue with only the episode that plays."
  (ui/page (queued 275)))
