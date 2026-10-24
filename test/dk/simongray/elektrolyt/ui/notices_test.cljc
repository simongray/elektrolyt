(ns dk.simongray.elektrolyt.ui.notices-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(deftest a-notice
  (let [x (notices/line {:label "Not saved"} "It failed." [:button "Try again"])]
    (is (= [:section.notice {:aria-label "Not saved"}] (subvec x 0 2))
        "a region of the page by its label")
    (is (= [icons/warning-circle] (map (comp :icon second) (elements controls/icon x)))
        "with an icon, which a screen reader skips")
    (is (= "It failed." (text-of (first (elements :p x)))))
    (is (= [[:button "Try again"]] (elements :button x)) "and what to do after the words"))
  (let [x (notices/line {:icon icons/info} "Something to know." nil)]
    (is (= :div.notice (first x)) "or no region, among other content")
    (is (= [icons/info] (map (comp :icon second) (elements controls/icon x))))))
