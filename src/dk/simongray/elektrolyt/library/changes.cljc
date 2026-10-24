(ns dk.simongray.elektrolyt.library.changes
  "Announced changes of the listener's profile, e.g. a dismissal from the
  inbox or a move in the queue, each with a change that undoes it until
  the next.")

(defn announced
  "The change that applies the `operation` to the library, with the
  `announcement` for a screen reader and the `undo`, if any, under
  `undo-key`."
  [undo-key announcement undo operation]
  {:operation operation
   :ui        #(assoc % :announcement announcement undo-key undo)})
