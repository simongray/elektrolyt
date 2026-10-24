(ns dk.simongray.elektrolyt.ui.controls-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui-test :refer [elements]]
            [replicant.alias :as alias]))

(deftest an-icon-button
  (let [tooltips #(map last (elements :title (alias/expand %)))]
    (is (= ["Pause"] (tooltips [controls/icon-button {:icon icons/pause} "Pause"]))
        "its words its icon's tooltip, as well as its name")
    (is (nil? (:title (second (alias/expand-1 [controls/icon-button {:icon icons/pause} "Pause"]))))
        "rather than the button's, which a screen reader might read twice")
    (is (= ["Back 15 seconds"]
           (tooltips [controls/icon-button {:icon [controls/icon {:icon icons/rewind} [:text "15"]]}
                      "Back " 15 " seconds"]))
        "of every part of them, on an icon that a view drew too")
    (is (empty? (tooltips [controls/button {:icon icons/play} "Play"]))
        "and none on a button whose words show")))

(deftest an-icon-placed-in-another
  (is (= "M 18 22 l 5 0 a 2 2 0 0 1 4 6 H 18 V 32 Z"
         (controls/placed "M10,20l10,0a4,4,0,0,1,8,12H10V40Z" 0.5 13 12))
      "its absolute coordinates moved and every length scaled, but an arc's flags kept")
  (is (= "M 1.33 0.5 L 2 2.25" (controls/placed "M.666.25L1,1.125" 2 0 0))
      "with its numbers to two decimals, however the path packs them"))

(deftest an-icon-among-words
  (is (= [controls/icon {:icon icons/envelope-simple :class "in-text" :title "Email" :style {:--drawn-to "206"}}]
         (controls/in-text icons/envelope-simple "Email"))
      "which sits on the baseline by where its drawing ends")
  (is (= [controls/icon {:icon icons/house :class "in-text"}] (controls/in-text icons/house))
      "or by the arrow's, with no tooltip"))

(deftest a-single-click
  (let [actions        [[:library/subscribe "https://example.org/feed.xml" "A Show"]]
        [[k n wrapped]] (controls/single-click actions)
        expanded       #(controls/unless-repeated {} % wrapped)]
    (is (= [:event/single-click [:event/click-count] actions] [k n wrapped]))
    (is (= actions (expanded 1)) "a click")
    (is (= actions (expanded 0)) "a click by the keyboard")
    (is (nil? (expanded 2)) "but not the second click of a double click")))
