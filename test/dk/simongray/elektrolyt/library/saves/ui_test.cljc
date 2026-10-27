(ns dk.simongray.elektrolyt.library.saves.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.saves.ui :as saves-ui]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui-test :refer [elements spoken text-of]]))

(defn unsaved
  "A state on the Library page whose writes failed `n` times in a row for
  the `cause`."
  [cause n]
  {:route       {:name :route/library}
   :save-status {:since #inst "2026-10-24T14:32:00Z" :failures n :cause cause}})

(defn buttons
  "The spoken names, icons and click actions of the buttons in the Hiccup
  `x`."
  [x]
  (map (juxt spoken (comp :icon second) #(get-in (second %) [:on :click]))
       (elements controls/button x)))

(deftest the-notice
  (let [x (saves-ui/notice (unsaved :other 2))]
    (is (= [:section.notice {:aria-label "Not saved"}] (subvec x 0 2))
        "a region of the page")
    (is (re-find #"aren't saved yet\.$" (text-of (first (elements :p x)))))
    (is (= [["Try again to save your changes" icons/arrows-clockwise [[:library/save]]]] (buttons x))
        "whose button says what it tries among the page's others")
    (is (= ["main h1"] (map (comp :data-refocus second) (elements controls/button x)))
        "and gives the focus to the page's heading as the notice goes"))
  (let [x (saves-ui/notice (unsaved :full 1))]
    (is (= [["/settings#this-browser" "Make room in Settings"]]
           (map (juxt (comp :href second) spoken) (elements :a x)))
        "with a link to where the listener can make room")
    (is (= ["Try again to save your changes"] (map first (buttons x)))))
  (is (= [["Reload the page" icons/arrows-clockwise [[:page/reload]]]]
         (buttons (saves-ui/notice (unsaved :newer-format 1)))))
  (is (nil? (saves-ui/notice (unsaved :other 1))) "and none after a single failure")
  (is (nil? (saves-ui/notice {}))))

(deftest before-the-pages-heading
  (let [paper (first (elements :div.paper (ui/page (unsaved :other 2))))
        kinds (keep #(when (vector? %) (first %)) (rest paper))]
    (is (= [:section.notice :h1] (take 2 kinds)))))
