(ns dk.simongray.podcast-app.ui.settings-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.ui.settings :as settings-ui]
            [dk.simongray.podcast-app.ui.show-test :refer [text-of]]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(defn by-id
  "The element of the Hiccup `x` with the `tag` and the `id`."
  [x tag id]
  (some #(when (= id (:id (second %))) %) (elements tag x)))

(defn chosen
  "The value of the option that the select `id` of the Hiccup `x` has
  selected."
  [x id]
  (some #(when (:selected (second %)) (:value (second %)))
        (elements :option (by-id x :select id))))

(deftest the-settings-page
  (let [x (settings-ui/content {:library inbox-test/lib})]
    (is (= ["Playback" "New episodes" "Subscriptions" "This browser"]
           (map text-of (elements :h2 (elements :legend x))))
        "each group a fieldset, its heading in its legend")
    (testing "the preferences as they are, the defaults at first"
      (is (= "15" (chosen x "skip-back")))
      (is (= "30" (chosen x "skip-forward")))
      (is (= "1" (chosen x "speed")))
      (is (= "chapters" (chosen x "track-buttons")))
      (is (= "inbox" (chosen x "default-new-episodes"))))
    (testing "each change applied at once"
      (is (= [[:settings/prefer :skip-back [:event/target-number]]]
             (get-in (by-id x :select "skip-back") [1 :on :change])))
      (is (= [[:settings/new-episodes [:event/target-keyword]]]
             (get-in (by-id x :select "default-new-episodes") [1 :on :change]))))
    (let [[_ attrs] (by-id x :input "continuous")]
      (is (= ["checkbox" "switch" false] ((juxt :type :role :checked) attrs))
          "continuous play, a switch")
      (is (= [[:settings/prefer :continuous? [:event/target-checked]]] (get-in attrs [:on :change]))))
    (is (= 3 (count (filter #(= "status" (:role (second %))) (elements :p.settings-status x))))
        "a status line beside the buttons of each group that has them")))

(deftest how-this-browser-keeps-the-library
  (let [shown #(text-of (settings-ui/this-browser {:storage %}))]
    (is (re-find #"Asking the browser" (shown nil)))
    (is (re-find #"may clear the library.*Ask the browser to keep it"
                 (shown {:supported? true :persisted? false :usage 1234567})))
    (is (re-find #"keeps the library until you clear.*1\.2 MB"
                 (shown {:supported? true :persisted? true :usage 1234567})))
    (is (re-find #"doesn't say" (shown {:supported? false})))))
