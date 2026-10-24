(ns dk.simongray.elektrolyt.inbox.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.inbox :as inbox]
            [dk.simongray.elektrolyt.inbox-test :refer [changed id state]]
            [dk.simongray.elektrolyt.inbox.ui :as inbox-ui]
            [dk.simongray.elektrolyt.queue.ui :as queue-ui]
            [dk.simongray.elektrolyt.queue.ui-test :refer [button-of]]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui-test :refer [elements spoken text-of]]))

(defn clicked
  "The actions of a click on the button of the Hiccup `x` whose text is
  `text`, inside the controls/single-click that wraps them, if any."
  [x text]
  (let [[[k _ actions] :as click] (get-in (button-of x text) [:on :click])]
    (if (= :event/single-click k) actions click)))

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
             (clicked x "Play last Episode 2")))
      (is (= [[:queue/add {:id (id 3)} "Episode 3" true] [:page/focus (inbox-ui/control-id (id 2) :next)]]
             (clicked x "Play next Episode 3")))
      (is (= [[:inbox/dismiss (id 1)] [:page/focus (inbox-ui/control-id (id 2) :dismiss)]]
             (clicked x "Dismiss Episode 1"))
          "or the row before, at the end"))
    (testing "with icons, which act once for a double click, since the next row moves up under the pointer"
      (is (= [icons/play icons/rows-plus-bottom icons/rows-plus-top icons/x]
             (map #(:icon (button-of x (str % " Episode 2"))) ["Play" "Play last" "Play next" "Dismiss"])))
      (is (every? #(= :event/single-click (ffirst (get-in (button-of x (str % " Episode 2")) [:on :click])))
                  ["Play" "Play last" "Play next" "Dismiss"])))
    (is (= [[:inbox/queue-all] [:page/focus-heading]] (clicked x "Add all to queue")))
    (is (= [[:inbox/dismiss-all] [:page/focus inbox-ui/undo-id]] (clicked x "Dismiss all")))))

(deftest the-last-episode
  (let [x (inbox-ui/content (state 1))]
    (is (= [:page/focus inbox-ui/undo-id] (second (clicked x "Dismiss Episode 1")))
        "a dismissal gives the focus to the undo that appears")
    (is (= [:page/focus-heading] (second (clicked x "Play Episode 1")))
        "and anything else to the page's heading")))

(deftest the-undo-of-a-dismissal
  (let [x (inbox-ui/content (changed inbox/dismissed (state 1 2) (id 2)))]
    (is (= [[:inbox/undo] [:page/focus (inbox-ui/control-id (id 2) :dismiss)]]
           (clicked x "Undo dismissing Episode 2"))
        "which gives the focus to the episode that it puts back"))
  (let [x (inbox-ui/content (changed inbox/all-dismissed (state 1 2)))]
    (is (re-find #"No new episodes" (text-of x)))
    (is (some? (button-of x "Undo dismissing all")) "and stays once the inbox is empty")
    (is (= "main h1" (:data-refocus (button-of x "Undo dismissing all")))
        "until another tab leaves it nothing to put back, when the focus goes to the heading"))
  (is (= "#inbox" (:data-refocus (button-of (inbox-ui/section (changed inbox/dismissed (state 1 2) (id 2)))
                                            "Undo dismissing Episode 2")))
      "the section's, on the Library page"))

(deftest an-empty-inbox
  (let [x (inbox-ui/content (state))]
    (is (re-find #"No new episodes" (text-of x)))
    (is (empty? (elements :ul.inbox x)))
    (is (nil? (button-of x "Dismiss all")) "nothing to dismiss")
    (is (= ["/library"] (map #(:href (second %)) (elements :a x)))
        "and links to the Library, whose shows' new episodes arrive here")))

(deftest the-inbox-from-an-empty-queue
  (is (re-find #"Your Inbox has 2 episodes to choose from"
               (text-of (queue-ui/content (state 1 2))))
      "an empty queue tells of the inbox"))
