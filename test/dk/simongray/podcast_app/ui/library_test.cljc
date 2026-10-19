(ns dk.simongray.podcast-app.ui.library-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.queue-test :as queue-test]
            [dk.simongray.podcast-app.ui.library :as library-ui]
            [dk.simongray.podcast-app.ui.player-test :refer [buttons]]
            [dk.simongray.podcast-app.ui-test :refer [elements text-of]]))

(defn attributes [tag x]
  (second (first (elements tag x))))

(deftest the-library-page
  (let [x (library-ui/content {:library queue-test/lib})]
    (is (= ["Inbox" "Shows"] (map text-of (mapcat #(elements % x) [:h2#inbox :h2#shows])))
        "the start of the inbox, and the shows")
    (is (= ["A Show"] (map text-of (elements :h3 (elements :ul x))))
        "a heading that links to each show")
    (is (re-find #"4 unplayed" (text-of x)))
    (is (= ["library-shows" "auto"] (:class (attributes :ul x))) "the layout that suits the screen")
    (is (= "false" (:aria-busy (attributes :ul x))))
    (is (= [[:library/refresh]] (get-in (first (buttons x)) [1 :on :click])))
    (is (= ["library-shows" "list"]
           (:class (attributes :ul (library-ui/content {:library      queue-test/lib
                                                         :library-view {:layout :list}}))))
        "or the listener's choice")))

(deftest a-refresh-under-way
  (let [x (library-ui/content {:library queue-test/lib :refresh {:running? true :done 3 :total 40}})]
    (is (re-find #"Checking 3 of 40 shows" (text-of x)))
    (is (= "true" (:aria-busy (attributes :ul x))))
    (is (= "true" (get-in (first (buttons x)) [1 :aria-disabled])) "a button that keeps the focus")))

(deftest an-empty-library
  (let [x (library-ui/content {:library (library/make "test")})]
    (is (re-find #"No shows yet" (text-of x)))
    (is (empty? (elements :ul x)))))
