(ns dk.simongray.elektrolyt.ui.facts-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui-test :refer [elements spoken text-of]]))

(deftest facts-in-a-line
  (let [x (facts-ui/line [nil "Explicit" nil "Complete"])]
    (is (= [:p "Explicit" facts-ui/separator "Complete"] x) "the facts that have a value")
    (is (= "Explicit, Complete" (spoken x))
        "with a dot between them, which a screen reader reads as a comma"))
  (is (nil? (facts-ui/line [nil nil]))))

(deftest the-time-it-has-left
  (is (= "1h" (text-of (facts-ui/time-left 3600 nil))) "its length at first")
  (is (= "50m left" (text-of (facts-ui/time-left 3600 {:position 600})))
      "and what's left of it once it's started, as a fact rather than in Resume")
  (is (= "1h" (text-of (facts-ui/time-left 3600 {:position 600 :played? true}))) "but not once it's played")
  (is (nil? (facts-ui/time-left nil {:position 600})) "and nothing without a length"))

(deftest where-an-episode-came-from
  (is (= "From drpodcast.nu" (facts-ui/source-text "https://drpodcast.nu/genstart/feed.xml")))
  (is (= "From example.org" (facts-ui/source-text "https://www.example.org/feed.xml")) "without www.")
  (is (= "From another feed" (facts-ui/source-text true)) "from a supplement that names no URL")
  (is (nil? (facts-ui/source-text nil)) "and nothing for its show's own")
  (let [icon-of #(select-keys (second (first (elements controls/icon %))) [:icon :title])
        mark    (facts-ui/source "https://drpodcast.nu/genstart/feed.xml")
        label   (facts-ui/labelled-source "https://drpodcast.nu/genstart/feed.xml")]
    (is (= {:icon icons/puzzle-piece :title "From drpodcast.nu"} (icon-of mark))
        "which a row marks with a puzzle piece, whose tooltip says it")
    (is (= "From drpodcast.nu" (spoken mark)) "as a screen reader does")
    (is (= [{:icon icons/puzzle-piece} "From drpodcast.nu"] [(icon-of label) (text-of label)])
        "and the episode's page with a puzzle piece before the words")
    (is (nil? (facts-ui/source nil)))
    (is (nil? (facts-ui/labelled-source nil)))))
