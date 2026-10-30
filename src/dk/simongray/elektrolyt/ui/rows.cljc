(ns dk.simongray.elektrolyt.ui.rows
  "The buttons of the rows of any list with an order, e.g. the queue: a
  button named by its row, Move up and Move down, and a grip that drags
  its row or opens the menu of its moves."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.menu :as menu]
            [dk.simongray.elektrolyt.ui.routes :as routes]))

(defn control-id
  "The id of the `kind` of button, e.g. :remove, of the row `id` in the
  list `list-name`, by which the focus finds it again after a change."
  [list-name id kind]
  (str list-name "-" (routes/short-hash (pr-str id)) "-" (name kind)))

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

(defn dragged
  "Where the row at index `from` of the `boxes`, the :top and :height of
  each row from the top of its list, goes as it's dragged `dy` pixels
  down, and how far each row moves down to make room for it, as [to
  shifts]. A row moves once the middle of the dragged one passes its own,
  by the dragged one's height and the gap between rows."
  [boxes from dy]
  (let [{:keys [top height]} (get boxes from)
        [a b]                boxes
        middle               #(+ (:top %) (/ (:height %) 2))
        dragged              (+ top (/ height 2) dy)
        room                 (+ height (if b (- (:top b) (:top a) (:height a)) 0))
        shifts               (vec (map-indexed (fn [i box]
                                                 (cond (and (< i from) (< dragged (middle box))) room
                                                       (and (< from i) (< (middle box) dragged)) (- room)
                                                       :else                                     0))
                                               boxes))]
    [(- (+ from (count (filter neg? shifts))) (count (filter pos? shifts)))
     shifts]))

(defn edge-speed
  "How far a scroller from `top` to `bottom` scrolls in a frame of a drag
  with the pointer at `y`: up as it nears the top, down as it nears the
  bottom, faster the nearer it is, and not at all in between."
  [top bottom y]
  (let [band 48]
    (cond (< y (+ top band))    (/ (- y top band) 4)
          (< (- bottom band) y) (/ (- y (- bottom band)) 4)
          :else                 0)))

(defn moves
  "The moves of the row at `position` of `n`, counted from 1, as the label,
  icon and new position of each: to the top and up, unless it's first,
  and down and to the bottom, unless it's last, but for one that would go
  where another does."
  [position n]
  (cond-> []
    (< 2 position)       (conj ["Move to top" icons/arrow-line-up 1])
    (< 1 position)       (conj ["Move up" icons/arrow-up (dec position)])
    (< position n)       (conj ["Move down" icons/arrow-down (inc position)])
    (< position (dec n)) (conj ["Move to bottom" icons/arrow-line-down n])))

;; Atlassian's guidance for Pragmatic drag and drop: a row moves by a drag
;; of its grip, or by the menu of its moves that a press on the grip opens
;; (ui/menu.cljc), which meets WCAG 2.2, SC 2.5.7 Dragging Movements on the
;; same page, and a keyboard without dragging by the arrow keys. Each move
;; is named by the row, and the focus goes back to the grip after it. A
;; select would offer the moves in the platform's own picker, but a
;; browser opens one at a press, which takes the pointer from a drag, and
;; Safari can't open one once a press turns out not to be a drag.
;; https://atlassian.design/components/pragmatic-drag-and-drop/accessibility-guidelines
;; https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html
(defn grip
  "The grip of the row `id` titled `title` in the list `list-name` in
  `state`, at `position` of `n`, counted from 1: a menu button that a drag
  moves the row by, among the rows that show, and whose press opens the
  menu of its moves. A move dispatches the action `move` with the new
  position added, e.g. [:queue/move id], and gives the grip the focus."
  [state list-name {:keys [id title position n move]}]
  (let [grip-id (control-id list-name id :grip)
        menu-id (control-id list-name id :moves)]
    (menu/menu state
               {:id     menu-id
                :attrs  {:class "grip"}
                :button {:id    grip-id
                         :class "icon-button"
                         :title "Move"
                         ;; with the menu closed as the drag starts
                         :on    {:pointerdown [[:page/drag-row move grip-id
                                                [(menu/kept-open menu-id false)]]]}}
                :label  (list (controls/icon-of icons/dots-six-vertical)
                              [:span.visually-hidden "Move " title])
                :items  (for [[label icon to] (moves position n)]
                          {:label   label
                           :icon    icon
                           :name    title
                           :actions [(conj move to) [:page/row-moved grip-id]]})})))
