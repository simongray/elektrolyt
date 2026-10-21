(ns dk.simongray.elektrolyt.changes
  "Changes of the listener's profile that a page announces, e.g. a
  dismissal from the inbox or a move in the queue, with the change that
  undoes the last of them, until the next.")

(defn announced
  "The change of the state that applies the `operation` to the library, as
  dk.simongray.elektrolyt.operations has it, with what a screen reader
  hears of it, the `announcement`, and the `undo` of it under the key
  `undo-key`, or none."
  [undo-key announcement undo operation]
  {:operation operation
   :ui        #(assoc % :announcement announcement undo-key undo)})
