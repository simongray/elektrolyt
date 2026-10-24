(ns dk.simongray.elektrolyt.ui.rows
  "The buttons of the rows of any list with an order, e.g. the queue: a
  button named by its row, and Move up and Move down."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.elektrolyt.ui.controls :as controls]))

(defn control-id
  "The id of the `kind` of button, e.g. :remove, of the row `id` in the
  list `list-name`, by which the focus finds it again after a change."
  [list-name id kind]
  (str list-name "-" (guid/short-hash (pr-str id)) "-" (name kind)))

(defn neighbour
  "The row that takes the place of the one at index `i` in the vector
  `rows` once it has gone: the next one, or else the one before."
  [rows i]
  (or (get rows (inc i)) (get rows (dec i))))

;; GOV.UK Design System, summary list: a visible label first, then the
;; name of what it acts on in visually hidden text, e.g. "Remove Episode
;; 3", so that the name begins with what a voice user says (WCAG 2.2, SC
;; 2.5.3 Label in Name)
(defn button
  "A button that says `label` for the row titled `title`, with the icon
  that `attrs` has as :icon, if any, and the rest of `attrs` merged in."
  [label title {:keys [icon] :as attrs}]
  (if icon
    [controls/button attrs label [:span.visually-hidden " " title]]
    [:button (merge {:type "button"} attrs)
     label [:span.visually-hidden " " title]]))

;; The APG's rearrangeable list: Move up and Move down, with aria-disabled
;; at either end, which keeps the focus on the button, and the focus on
;; the moved row after a move
;; https://www.w3.org/WAI/ARIA/apg/patterns/listbox/examples/listbox-rearrangeable/
;;
;; TODO: dragging too, with its look, and only on top of the buttons, which
;; meet WCAG 2.2, SC 2.5.7 Dragging Movements on their own. Atlassian's
;; guidance for Pragmatic drag and drop: a handle that's also the button of
;; the row's moves, and no dragging with the arrow keys.
;; https://atlassian.design/components/pragmatic-drag-and-drop/accessibility-guidelines
(defn move-buttons
  "The Move up and Move down buttons of the row titled `title` at
  `position` of `n`, counted from 1. Their ids come from `id-of` for :up
  and :down, and a click dispatches the action from `move-to` for the new
  position and keeps the focus on the button."
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
