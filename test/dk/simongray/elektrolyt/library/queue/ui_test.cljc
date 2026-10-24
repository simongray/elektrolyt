(ns dk.simongray.elektrolyt.library.queue.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.library.queue-test :as queue-test :refer [id state]]
            [dk.simongray.elektrolyt.library.queue.ui :as queue-ui]
            [dk.simongray.elektrolyt.player.ui-test :refer [buttons]]
            [dk.simongray.elektrolyt.ui-test :refer [elements text-of]]))

(defn button-of
  "The attributes of the button of the Hiccup `x` whose text is `text`."
  [x text]
  (some #(when (= text (text-of %)) (second %)) (buttons x)))

(deftest the-queue-page
  (let [x (queue-ui/content (state true 1 2 3))]
    (is (re-find #"Now playingEpisode 1" (text-of (elements :section x))))
    (is (re-find #"2 episodes, 20m"(text-of x)) "how many are up next, and for how long")
    (is (= ["Episode 2" "Episode 3"] (map text-of (elements :h3 (elements :ol.queue x)))))
    (testing "the buttons of a row, named by the episode"
      (is (some? (button-of x "Remove Episode 2")))
      (is (= "true" (:aria-disabled (button-of x "Move up Episode 2"))) "no way up from the top")
      (is (= "true" (:aria-disabled (button-of x "Move down Episode 3"))) "nor down from the end")
      (is (= [[:queue/move (id 2) 2] [:page/focus (queue-ui/control-id (id 2) :down)]]
             (get-in (button-of x "Move down Episode 2") [:on :click]))
          "a move keeps the focus on the button that moved it")
      (is (= [[:queue/remove (id 2)] [:page/focus (queue-ui/control-id (id 3) :remove)]]
             (get-in (button-of x "Remove Episode 2") [:on :click]))
          "and a removal moves it to the next row's")
      (is (= [[:queue/remove (id 3)] [:page/focus (queue-ui/control-id (id 2) :remove)]]
             (get-in (button-of x "Remove Episode 3") [:on :click]))
          "or the one before, at the end"))
    (is (= [[:dialog/open queue-ui/clear-dialog-id]]
           (get-in (button-of x "Clear the queue") [:on :click])))))

(deftest the-undo-of-the-last-change
  (let [removed (queue-test/changed queue/removed (state true 1 2 3) (id 2))
        x       (queue-ui/content removed)]
    (is (= [[:queue/undo] [:page/focus (queue-ui/control-id (id 2) :remove)]]
           (get-in (button-of x "Undo removing Episode 2") [:on :click]))
        "which gives the focus to the episode that it puts back")
    (is (= "#up-next" (:data-refocus (button-of x "Undo removing Episode 2")))
        "or to the heading, if it goes while it has the focus")
    (is (nil? (button-of (queue-ui/content (queue-test/elsewhere removed profile.queue/enqueue (id 2)))
                         "Undo removing Episode 2"))
        "which it does once another tab queued the episode again")))

(deftest an-empty-queue
  (let [x (queue-ui/content (state false))]
    (is (re-find #"Nothing in the queue" (text-of x)))
    (is (empty? (elements :ol.queue x)))
    (is (not-any? #(= [[:dialog/open queue-ui/clear-dialog-id]] (get-in % [1 :on :click])) (buttons x))
        "nothing to clear")))
