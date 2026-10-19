(ns dk.simongray.podcast-app.ui.inbox-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.inbox-test :refer [id state]]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.inbox :as inbox-ui]
            [dk.simongray.podcast-app.ui.queue :as queue-ui]
            [dk.simongray.podcast-app.ui.queue-test :refer [button-of]]
            [dk.simongray.podcast-app.ui-test :refer [elements spoken text-of]]))

(defn clicked
  "The actions of a click on the button of the Hiccup `x` whose text is
  `text`."
  [x text]
  (get-in (button-of x text) [:on :click]))

(deftest the-inbox-page
  (let [x (inbox-ui/content (state 1 2 3))]
    (is (re-find #"^3 new episodes" (text-of x)))
    (is (= ["Episode 3" "Episode 2" "Episode 1"] (map text-of (elements :h2 (elements :ul.inbox x)))))
    (is (re-find #"A Show, 2 Oct 2026"(spoken (elements :p (elements :ul.inbox x))))
        "each with its show and date, apart when read aloud")
    (testing "the buttons of a row, named by the episode, which move the focus to the next row's"
      (is (= [[:player/play {:id (id 2)} nil] [:page/focus (inbox-ui/control-id (id 1) :play)]]
             (clicked x "Play Episode 2")))
      (is (= [[:queue/add {:id (id 2)} "Episode 2" false] [:page/focus (inbox-ui/control-id (id 1) :queue)]]
             (clicked x "Add to queue Episode 2")))
      (is (= [[:queue/add {:id (id 3)} "Episode 3" true] [:page/focus (inbox-ui/control-id (id 2) :next)]]
             (clicked x "Play next Episode 3")))
      (is (= [[:inbox/dismiss (id 1)] [:page/focus (inbox-ui/control-id (id 2) :dismiss)]]
             (clicked x "Dismiss Episode 1"))
          "or the row before, at the end"))
    (is (= [[:inbox/queue-all] [:page/focus-heading]] (clicked x "Add all to queue")))
    (is (= [[:inbox/dismiss-all] [:page/focus inbox-ui/undo-id]] (clicked x "Dismiss all")))))

(deftest the-last-episode
  (let [x (inbox-ui/content (state 1))]
    (is (= [:page/focus inbox-ui/undo-id] (second (clicked x "Dismiss Episode 1")))
        "a dismissal gives the focus to the undo that appears")
    (is (= [:page/focus-heading] (second (clicked x "Play Episode 1")))
        "and anything else to the page's heading")))

(deftest the-undo-of-a-dismissal
  (let [x (inbox-ui/content (inbox/dismissed (state 1 2) (id 2)))]
    (is (= [[:inbox/undo] [:page/focus (inbox-ui/control-id (id 2) :dismiss)]]
           (clicked x "Undo dismissing Episode 2"))
        "which gives the focus to the episode that it puts back"))
  (let [x (inbox-ui/content (inbox/all-dismissed (state 1 2)))]
    (is (re-find #"No new episodes" (text-of x)))
    (is (some? (button-of x "Undo dismissing all")) "and stays once the inbox is empty")))

(deftest an-empty-inbox
  (let [x (inbox-ui/content (state))]
    (is (re-find #"No new episodes" (text-of x)))
    (is (empty? (elements :ul.inbox x)))
    (is (nil? (button-of x "Dismiss all")) "nothing to dismiss")
    (is (= ["/library" "/settings#new-episodes"] (map #(:href (second %)) (elements :a x)))
        "and links to where new episodes can go elsewhere, the group of the settings for it")))

(deftest the-count-of-the-inbox
  (is (= "Inbox 3 new episodes" (text-of (ui/nav-label (state 1 2 3) :route/inbox))))
  (is (= "Inbox 1 new episode" (text-of (ui/nav-label (state 1) :route/inbox))))
  (is (= "Inbox" (ui/nav-label (state) :route/inbox)) "none while it's empty")
  (is (re-find #"Your Inbox has 2 episodes to choose from"
               (text-of (queue-ui/content (state 1 2))))
      "and an empty queue tells of it"))
