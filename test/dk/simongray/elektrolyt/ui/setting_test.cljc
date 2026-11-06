(ns dk.simongray.elektrolyt.ui.setting-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]
            [dk.simongray.elektrolyt.ui.setting :as setting]))

(deftest a-row
  (let [bare (setting/action "a" "A title" nil)]
    (is (= "A title" (text-of bare)))
    (is (empty? (elements :p bare)) "has no hint unless it's given one")
    (is (empty? (elements :div.setting-control bare)) "and no room for a control that it doesn't have")))

(deftest a-switch
  (let [x         (setting/switch "s" "A switch" "Its hint." nil [:act [:event/target-checked]])
        [_ attrs] (first (elements :input x))
        [_ label] (first (elements :label x))
        [_ hint]  (first (elements :p x))]
    (is (= "s" (:for label) (:id attrs)) "takes its name from the label")
    (is (= (:id hint) (:aria-describedby attrs)) "and its description from the hint")
    (is (= ["checkbox" "switch" false] ((juxt :type :role :checked) attrs)))
    (is (= [[:act [:event/target-checked]]] (get-in attrs [:on :change])))))

(deftest a-file-picker
  (let [x         (setting/file "f" "A title" "Its hint." {:accept ".opml"})
        [_ attrs] (first (elements :input.visually-hidden x))]
    (is (= ["file" ".opml"] ((juxt :type :accept) attrs)) "stays an input, hidden visually so it takes the focus")
    (is (= ["f" "f"] (map #(:for (second %)) (concat (elements :label x) (elements :label.with-icon.file-button x))))
        "with the row's title and the button's words both naming it")
    (is (re-find #"Choose a file$" (text-of x)))))
