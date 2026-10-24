(ns dk.simongray.elektrolyt.library.saves.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.saves.ui :as saves-ui]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(defn unsaved
  "A state on the Library page whose writes failed `n` times in a row for
  the `cause`."
  [cause n]
  {:route       {:name :route/library}
   :save-status {:since #inst "2026-10-24T14:32:00Z" :failures n :cause cause}})

(defn buttons
  "The words and click actions of the buttons in the Hiccup `x`."
  [x]
  (map (juxt text-of #(get-in (second %) [:on :click])) (elements :button x)))

(deftest the-banner
  (let [x (saves-ui/banner (unsaved :other 2))]
    (is (= [:section.save-banner {:aria-labelledby "save-banner"}] (subvec x 0 2))
        "a region labelled by its heading")
    (is (= "Not saved" (text-of (first (elements :h2#save-banner x)))))
    (is (= [["Try again now" [[:library/save]]]] (buttons x))))
  (is (= [["Clear the searches and shows" [[:cache/clear]]] ["Try again now" [[:library/save]]]]
         (buttons (saves-ui/banner (unsaved :full 1))))
      "with the fix for its cause")
  (is (= [["Reload" [[:page/reload]]]] (buttons (saves-ui/banner (unsaved :newer-format 1)))))
  (is (= ["main h1" "main h1"]
         (map (comp :data-refocus second) (elements :button (saves-ui/banner (unsaved :full 1)))))
      "which give the focus to the page's heading as the banner goes")
  (is (nil? (saves-ui/banner (unsaved :other 1))) "and none after a single failure")
  (is (nil? (saves-ui/banner {}))))

(deftest before-the-pages-heading
  (let [main  (first (elements :main#main (ui/page (unsaved :other 2))))
        kinds (keep #(when (vector? %) (first %)) (rest main))]
    (is (= [:section.save-banner :h1] (take 2 kinds)))))
