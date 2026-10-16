(ns dk.simongray.podcast-app.ui.episode-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.episode-test :as episode-test]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [dk.simongray.podcast-app.ui.show-test :refer [text-of]]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(defn episode-page
  "The state of the Episode page of the second episode of the test's feed,
  as the Show page loaded it, at the query `t`, with the `more` state
  merged in."
  ([t]
   (episode-page t {}))
  ([t more]
   (-> (episode-test/loaded false :loading)
       (assoc :route {:name         :route/episode
                      :path-params  {:guid episode-test/g :hash episode-test/hash-2}
                      :query-params (cond-> {:url episode-test/url} t (assoc :t t))})
       (merge more))))

(deftest the-times-of-an-episode
  (is (= "12:34" (episode-ui/clock-text 754)))
  (is (= "1:02:03" (episode-ui/clock-text 3723)))
  (is (= "0:05" (episode-ui/clock-text 5.7)) "to the second below")
  (is (= [:time {:datetime "PT754S"} "12:34"] (episode-ui/clock 754))))

(deftest a-time-links-to-the-page-and-plays
  (let [page (episode-ui/route-page (episode-page nil))
        [_ {:keys [href on]} _] (episode-ui/time-link page 754 "12:34")]
    (is (re-find #"\?url=.*&t=754$" href) "a link that can be copied or opened")
    (is (= [[:event/prevent-default] [:player/play (episode/playable page) 754]]
           (:click on))
        "a click that plays from there")))

(deftest the-start-of-a-link
  (is (= 73 (episode-ui/start-time (episode-page "73"))))
  (is (= 73 (episode-ui/start-time (episode-page "1m13s"))))
  (is (nil? (episode-ui/start-time (episode-page nil)))))

(deftest the-play-button
  (let [page   (episode-ui/route-page (episode-page nil))
        button #(episode-ui/play-button page %1 %2)]
    (is (= "Play" (text-of (button nil nil))))
    (is (= "Play from 1:13" (text-of (button nil 73))) "from the time of the link")
    (is (= [[:player/play (episode/playable page) 73]]
           (get-in (button nil 73) [1 :on :click])))
    (is (= "Play" (text-of (button {:id (:id page) :playing? false} nil))) "while it's paused")
    (is (= [[:player/pause]] (get-in (button {:id (:id page) :playing? true} nil) [1 :on :click]))
        "and Pause while it plays, with no aria-pressed")
    (is (= "Play" (text-of (button {:id ["other" "x"] :playing? true} nil))) "while another one plays")))

(deftest the-page-of-an-episode
  (let [x (ui/page (episode-page nil))]
    (is (= "Episode 2: The Present – Podcasts" (ui/document-title (episode-page nil))))
    (is (= ["h1" "h2" "h3"]
           (keep #(when (vector? %) (re-find #"^h[1-6]" (name (first %))))
                 (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) (elements :main#main x))))
        "the title, Show notes, and the notes' own heading below it")
    (is (= [(pr-str [episode-test/g "https://example.org/?p=2"])]
           (keep #(:replicant/key (second %)) (filter (comp map? second) (elements :div x))))
        "a key of its own for each episode, so that its files are read when it mounts")
    (is (re-find #"A Show" (text-of (elements :div.episode-intro x))))))

(deftest the-transcript-on-request
  (let [page  (episode-ui/route-page (episode-page nil))
        doc   {:segments [{:start-time 0.0 :speaker "Adam" :body "Hello."}]}
        shown #(text-of (episode-ui/transcript page %))]
    (is (= "TranscriptShow the transcript" (shown nil)) "closed, and not read yet")
    (is (= "TranscriptShow the transcriptLoading the transcript…" (shown {:transcript-status :reading})))
    (is (= "TranscriptShow the transcript0:00 Adam: Hello."
           (shown {:transcript-status :read :transcript {:document doc}})))
    (is (= "TranscriptShow the transcriptThe transcript couldn't be read."
           (shown {:transcript-status :read :transcript {:error {:message "404"}}})))))

(deftest an-episode-that-isnt-there
  (testing "while the feed is on its way"
    (is (= "Loading the episode…" (text-of (episode-ui/missing (episode-page nil))))))
  (testing "once the whole feed doesn't have it"
    (let [state (assoc (episode-test/loaded true :done)
                       :route {:name         :route/episode
                               :path-params  {:guid episode-test/g :hash "0000000"}
                               :query-params {:url episode-test/url}})
          x     (episode-ui/missing state)]
      (is (re-find #"isn't in its show's feed" (text-of x)))
      (is (= [(str "/shows/" episode-test/g "?url=https%3A%2F%2Fexample.org%2Ffeed.xml")]
             (map #(:href (second %)) (elements :a x)))
          "with a link to the show"))))
