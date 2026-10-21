(ns dk.simongray.elektrolyt.ui.facts-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui-test :refer [spoken]]))

(deftest facts-in-a-line
  (let [x (facts-ui/line [nil "Explicit" nil "Complete"])]
    (is (= [:p "Explicit" facts-ui/separator "Complete"] x) "the facts that have a value")
    (is (= "Explicit, Complete" (spoken x))
        "with a dot between them, which a screen reader reads as a comma"))
  (is (nil? (facts-ui/line [nil nil]))))
