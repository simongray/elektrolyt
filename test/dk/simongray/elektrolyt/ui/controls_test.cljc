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
