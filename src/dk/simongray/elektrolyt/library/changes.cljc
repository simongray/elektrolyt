(ns dk.simongray.elektrolyt.library.changes
  "Announced changes of the listener's profile, e.g. a dismissal from the
  inbox or a move in the queue, each with a change that undoes it until
  the next.

  How an undo puts episodes back is a map of:

  - :undo-key, where the state keeps the undo
  - :restorable, a function of the profile and the undo, which gives the
    ids still as its change left them
  - :place, the words for where they go back, e.g. \"queue\"
  - :operation, the operation that puts them back"
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.ui.text :as text]))

(defn announced
  "The change that applies the `operation` to the library, with the
  `announcement` for a screen reader and the `undo`, if any, under
  `undo-key`."
  [undo-key announcement undo operation]
  {:operation operation
   :ui        #(assoc % :announcement announcement undo-key undo)})

(defn offered
  "The undo in `state` with only the :ids that it would put back by the
  `rules`, or nil when there are none: those still as its change left them,
  of shows that the library still has."
  [{:keys [library] :as state} {:keys [undo-key restorable] :as rules}]
  (let [undo (get state undo-key)]
    (when-let [ids (some->> (some-> library :profile (restorable undo))
                            (filterv #(library/loaded? library (first %)))
                            not-empty)]
      (assoc undo :ids ids))))

(defn undone
  "The change of `state` that puts back what its undo offers by the
  `rules`, or nil when there's nothing to put back. Putting back only some
  of them says how many, e.g. \"Put 2 of 3 episodes back in the queue\"."
  [state {:keys [undo-key place operation] :as rules}]
  (let [undo (get state undo-key)]
    (when-let [{:keys [ids] :as kept} (offered state rules)]
      (announced undo-key
                 (if (= (count ids) (count (:ids undo)))
                   (:done undo)
                   (str "Put " (count ids) " of " (text/counted (count (:ids undo)) "episode")
                        " back in the " place))
                 nil
                 [operation kept]))))
