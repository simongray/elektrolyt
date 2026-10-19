(ns dk.simongray.podcast-app.ui.inbox-scenes
  "Scenes of the Inbox page: the new episodes with their buttons, the undo
  of a dismissal, and an inbox with nothing in it."
  (:require [dk.simongray.mind-meld :as crdt]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.scenes :refer [id lib]]
            [dk.simongray.podcast-app.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defn arrived
  "The state of the Inbox page with the episodes `is` in the inbox."
  [& is]
  {:route   {:name :route/inbox}
   :library (reduce (fn [l i]
                      (library/update-profile l #(crdt/write %1 [:inbox (id i)] (constantly {:removed? false}) %2)))
                    lib
                    is)})

(defscene the-inbox
  "Four new episodes, the newest first, each with its buttons, and the
  count in the navigation."
  (ui/page (arrived 272 273 274 275)))

(defscene after-a-dismissal
  "The undo of the episode that the listener just dismissed, which stays
  until the next change."
  (ui/page (inbox/dismissed (arrived 272 273 274 275) (id 274))))

(defscene after-dismissing-all
  "An inbox that the listener emptied, with the undo."
  (ui/page (inbox/all-dismissed (arrived 272 273 274 275))))

(defscene nothing-new
  "An inbox with no episodes, which tells how they get there."
  (ui/page (arrived)))
