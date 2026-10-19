(ns dk.simongray.podcast-app.changes
  "Changes of the listener's profile that a page announces, e.g. a
  dismissal from the inbox or a move in the queue, with the change that
  undoes the last of them, until the next."
  (:require [dk.simongray.podcast.app.library :as library]))

(defn changed
  "The `state` with its library changed by the function of the profile
  `f` and its `args`, as library/update-profile takes them, what a screen
  reader hears of it, the `announcement`, and the `undo` of it under the
  key `undo-key`, or none."
  [state undo-key announcement undo f & args]
  (-> (apply update state :library library/update-profile f args)
      (assoc :announcement announcement undo-key undo)))
