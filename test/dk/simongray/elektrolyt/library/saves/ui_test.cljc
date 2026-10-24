(ns dk.simongray.elektrolyt.library.saves.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.saves.ui :as saves-ui]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(defn unsaved
  "A state on the Library page whose writes failed `n` times in a row for
  the `cause`."
  [cause n]
  {:route       {:name :route/library}
   :save-status {:since #inst "2026-10-24T14:32:00Z" :failures n :cause cause}})

(defn buttons
  "The words, icons and click actions of the buttons in the Hiccup `x`."
  [x]
  (map (juxt text-of (comp :icon second) #(get-in (second %) [:on :click]))
       (elements controls/button x)))

(deftest the-line
  (let [x (saves-ui/banner (unsaved :other 2))]
    (is (= [:section.save-banner {:aria-label "Not saved"}] (subvec x 0 2))
        "a region of its own")
    (is (= [icons/warning-circle] (map (comp :icon second) (elements controls/icon x))))
    (is (re-find #"aren't saved yet\.$" (text-of (first (elements :p x)))))
    (is (= [["Try again" icons/arrows-clockwise [[:library/save]]]] (buttons x)))
    (is (= ["main h1"] (map (comp :data-refocus second) (elements controls/button x)))
        "which gives the focus to the page's heading as the line goes"))
  (let [x (saves-ui/banner (unsaved :full 1))]
    (is (= ["/settings#this-browser"] (map (comp :href second) (elements :a x)))
        "with a link to where the listener can make room")
    (is (= ["Try again"] (map first (buttons x)))))
  (is (= [["Reload" icons/arrows-clockwise [[:page/reload]]]]
         (buttons (saves-ui/banner (unsaved :newer-format 1)))))
  (is (nil? (saves-ui/banner (unsaved :other 1))) "and none after a single failure")
  (is (nil? (saves-ui/banner {}))))

(deftest before-the-pages-heading
  (let [main  (first (elements :main#main (ui/page (unsaved :other 2))))
        kinds (keep #(when (vector? %) (first %)) (rest main))]
    (is (= [:section.save-banner :h1] (take 2 kinds)))))
