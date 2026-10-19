(ns dk.simongray.podcast-app.ui.home-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.queue-test :refer [state]]
            [dk.simongray.podcast-app.ui.home :as home]
            [dk.simongray.podcast-app.ui-test :refer [elements nodes text-of]]))

(deftest the-start-of-the-queue
  (let [x (home/up-next (state true 1 2 3 4))]
    (is (= ["Episode 1" "Episode 2" "Episode 3" "Episode 4"] (map text-of (elements :h3 x)))
        "what plays and the next three")
    (is (re-find #"Now playing.*A Show" (text-of (first (elements :article.queue-episode x))))
        "marked as playing")
    (is (= "The whole queue: 4 episodes, 40m" (text-of (elements :p.list-more x)))
        "and a link to the whole queue")
    (is (= "/queue" (:href (second (first (elements :a (elements :p.list-more x))))))))
  (is (re-find #"Nothing in the queue" (text-of (home/up-next (state false))))))

(defn headings
  "The text of the h2s of the content of Home in the `state`, with an id
  in their tags or not."
  [state]
  (->> (nodes (home/content state))
       (filter #(and (vector? %) (re-find #"^:h2\b" (str (first %)))))
       (map text-of)))

(deftest about-first-for-a-new-listener
  (is (= ["About" "Up next" "You might like"] (headings {:library (library/make "test")})))
  (is (= ["Up next" "You might like" "About"] (headings (state false)))
      "and last for one who has shows")
  (is (= ["About"] (headings {})) "and alone without the library"))
