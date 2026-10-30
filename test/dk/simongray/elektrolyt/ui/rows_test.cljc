(ns dk.simongray.elektrolyt.ui.rows-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(deftest the-moves-of-a-row
  (let [moves #(map (juxt first last) (rows/moves %1 %2))]
    (is (= [["Move down" 2] ["Move to bottom" 4]] (moves 1 4)) "down and to the bottom from the top")
    (is (= [["Move up" 1] ["Move down" 3] ["Move to bottom" 4]] (moves 2 4))
        "and up from second place, but not to the top, where up goes")
    (is (= [["Move to top" 1] ["Move up" 2]] (moves 3 3)) "only up from the bottom")
    (is (empty? (moves 1 1)) "and none for a row alone")))

(deftest a-dragged-row
  (let [boxes [{:top 0 :height 90} {:top 100 :height 90} {:top 200 :height 90}]]
    (is (= [0 [0 0 0]] (rows/dragged boxes 0 90)) "stays until its middle passes the next one's")
    (is (= [1 [0 -100 0]] (rows/dragged boxes 0 110))
        "which then makes room above it, by its height and the gap")
    (is (= [2 [0 -100 -100]] (rows/dragged boxes 0 210)) "as do those after it")
    (is (= [1 [0 100 0]] (rows/dragged boxes 2 -110)) "and the rows above make room below it")))

(deftest scrolling-at-the-edges
  (is (zero? (rows/edge-speed 0 500 250)) "not in the middle")
  (is (neg? (rows/edge-speed 0 500 10)) "up near the top")
  (is (< 0 (rows/edge-speed 0 500 460) (rows/edge-speed 0 500 490)) "and down near the bottom, faster nearer it"))

(deftest a-grip
  (let [x                    (rows/grip {} "queue" {:id 7 :title "Episode 7" :position 2 :n 3 :move [:queue/move 7]})
        [_ attrs :as button] (first (elements :button x))
        moves                (elements controls/button x)]
    (is (= ["Move Episode 7" "Move"] [(text-of button) (:title attrs)])
        "a menu button named by its row, with Move as its tooltip")
    (is (= [[:page/drag-row [:queue/move 7] (:id attrs)
             [[:store/assoc-in [:open [:menu (rows/control-id "queue" 7 :moves)]] false]]]]
           (get-in attrs [:on :pointerdown]))
        "which drags the row from a press, with its menu closed as the drag starts")
    (is (= ["Move up Episode 7" "Move down Episode 7"] (map text-of moves))
        "and opens the menu of the row's moves, named by it")
    (is (= [[:queue/move 7 3] [:page/row-moved (:id attrs)]]
           (take 2 (get-in (second moves) [1 :on :click])))
        "each of which moves the row, without the rows' entrances")))
