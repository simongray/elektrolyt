(ns dk.simongray.elektrolyt.ui.rows
  "The buttons of the rows of a list that the listener acts on: a button
  named by its row, and the buttons that move a row up and down, e.g. in
  the queue and in the directories of the settings, with their ids and
  the row that takes the focus once one has gone.

  The views give the actions, so the rows can be anything that has an
  order, e.g. the queue of the profile or a preference."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.elektrolyt.ui.controls :as controls]))

(defn control-id
  "The id of the button `kind`, e.g. :remove, of the row `id` of the list
  named `list-name`, by which the focus finds it again after a change."
  [list-name id kind]
  (str list-name "-" (guid/short-hash (pr-str id)) "-" (name kind)))

(defn neighbour
  "The row that takes the place of the row at index `i` of the `rows`, a
  vector, once it has gone: the next one, or else the one before it."
  [rows i]
  (or (get rows (inc i)) (get rows (dec i))))

;; GOV.UK Design System, summary list: a visible label first, then the
;; name of what it acts on in visually hidden text, e.g. "Remove Episode
;; 3", so that the name begins with what a voice user says (WCAG 2.2, SC
;; 2.5.3 Label in Name)
(defn button
  "A button of the row titled `title` that says `label`, after the icon
  that the `attrs` have as :icon, if any, with the rest of the `attrs`
  merged in."
  [label title {:keys [icon] :as attrs}]
  (if icon
    [controls/button attrs label [:span.visually-hidden " " title]]
    [:button (merge {:type "button"} attrs)
     label [:span.visually-hidden " " title]]))

;; The APG's rearrangeable list: move up and move down, with aria-disabled
;; at either end, which keeps the focus on the button, and the focus on
;; the moved row after a move
;; https://www.w3.org/WAI/ARIA/apg/patterns/listbox/examples/listbox-rearrangeable/
;;
;; TODO: dragging too, with the look, and only on top of the buttons, which
;; meet WCAG 2.2, SC 2.5.7 Dragging Movements on their own. Atlassian's
;; guidance for Pragmatic drag and drop: a handle that is also the button
;; of the row's moves, and no dragging with the arrow keys.
;; https://atlassian.design/components/pragmatic-drag-and-drop/accessibility-guidelines
(defn move-buttons
  "The Move up and Move down buttons of the row titled `title` at
  `position` of `n`, counted from 1, with the ids that `id-of` gives for
  :up and :down, whose click dispatches the action that `move-to` gives
  for the position to move to, and keeps the focus on the button."
  [title position n id-of move-to]
  (list
   (button "Move up" title
           (if (= 1 position)
             {:id (id-of :up) :aria-disabled "true"}
             {:id (id-of :up)
              :on {:click [(move-to (dec position)) [:page/focus (id-of :up)]]}}))
   (button "Move down" title
           (if (= n position)
             {:id (id-of :down) :aria-disabled "true"}
             {:id (id-of :down)
              :on {:click [(move-to (inc position)) [:page/focus (id-of :down)]]}}))))
