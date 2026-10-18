(ns dk.simongray.podcast-app.ui.episode-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.episode-test :as episode-test]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [dk.simongray.podcast-app.ui.show-test :refer [spoken text-of]]
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
  (is (= [:time {:datetime "PT754S"} "12:34"] (episode-ui/clock 754)))
  (is (= [:time {:datetime "PT3723S"} "1:02:03"] (episode-ui/clock 3723)))
  (is (= [:time {:datetime "PT5S"} "0:05"] (episode-ui/clock 5.7)) "to the second below"))

(deftest the-chapters-and-their-links
  (let [page  (episode-ui/route-page (episode-page nil))
        items (->> (episode-ui/chapters page [{:start-time 0 :title "Intro" :url "https://example.org/intro"}
                                              {:start-time 60 :title "Sneaky" :url "javascript:alert(1)"}
                                              {:start-time 90 :url "https://example.org/untitled"}
                                              {:start-time 120 :title "Plain"}])
                   (elements :li))]
    (is (= [[:a {:href "https://example.org/intro" :target "_blank" :aria-describedby "new-tab-note"} "Intro"]
            "Sneaky"
            [:a {:href "https://example.org/untitled" :target "_blank" :aria-describedby "new-tab-note"}
             "https://example.org/untitled"]
            "Plain"]
           (map last items))
        "a link to the web alone, in a new tab, and the URL of a chapter without a title")))

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
  (let [page    (episode-ui/route-page (episode-page nil))
        playing #(hash-map :id %1 :media {:paused (not %2)})
        button  #(episode-ui/play-button page %1 %2 %3)]
    (is (= "Play" (text-of (button nil nil nil))))
    (is (= "Play from 1:13" (text-of (button nil nil 73))) "from the time of the link")
    (is (= [[:player/play (episode/playable page) 73]]
           (get-in (button nil nil 73) [1 :on :click])))
    (is (= "Resume, 50 min left" (text-of (button nil {:position 600} nil)))
        "where the listener left it")
    (is (= "Play" (text-of (button nil {:position 600 :played? true} nil))) "unless it's played")
    (is (= "Resume, 50 min left" (text-of (button (playing (:id page) false) {:position 600} nil)))
        "while it's paused")
    (is (= [[:player/pause]] (get-in (button (playing (:id page) true) nil nil) [1 :on :click]))
        "and Pause while it plays, with no aria-pressed")
    (is (= "Play" (text-of (button (playing ["other" "x"] true) nil nil))) "while another one plays")))

(deftest the-buttons-that-queue-it
  (let [page    (episode-ui/route-page (episode-page nil))
        texts   #(map text-of (episode-ui/queue-buttons page %))]
    (is (= ["Play next" "Add to queue"] (texts nil)))
    (is (= ["Play next" "Remove from queue"] (texts :queued)))
    (is (empty? (texts :current)) "none for the episode that plays")
    (is (= [[:queue/add (episode/playable page) (:title page) true]]
           (get-in (first (episode-ui/queue-buttons page nil)) [1 :on :click])))))

(deftest the-page-of-an-episode
  (let [x (ui/page (episode-page nil))]
    (is (= "Episode 2: The Present – Podcasts" (ui/document-title (episode-page nil))))
    (is (= ["h1" "h2" "h3" "h2"]
           (keep #(when (vector? %) (re-find #"^h[1-6]" (name (first %))))
                 (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) (elements :main#main x))))
        "the title, Show notes, the notes' own heading below it, and the Details")
    (let [[article] (elements :article.with-details x)]
      (is (= (pr-str [episode-test/g "https://example.org/?p=2"]) (:replicant/key (second article)))
          "the episode as an article, with a key of its own, so that its files are read when it mounts")
      (is (= [:aside.details {:aria-labelledby "details"}] (subvec (last article) 0 2))
          "the Details last, after the show notes")
      (let [[header] (elements :header.episode-intro article)]
        (is (= ["Episode 2: The Present" "A Show"] (map text-of (rest (second header))))
            "its header beginning with its heading, and its show as the subtitle")))))

(deftest the-numbers-of-an-episode
  (is (= ["Season 2" "Episode 3" nil] (episode-ui/numbering-facts {:type "full" :season 2 :episode 3})))
  (is (= ["The Sequel" "Ch.3" "Bonus"]
         (episode-ui/numbering-facts {:type "bonus" :season 2 :season-name "The Sequel"
                                      :episode 204 :episode-display "Ch.3"}))
      "a season's name and an episode's display text in place of their numbers")
  (is (= [nil "Episode 3.5" "Trailer"] (episode-ui/numbering-facts {:type "trailer" :episode 3.5})))
  (is (= "3" (episode-ui/number-text 3.0))))

(deftest the-facts-of-an-episode
  (let [page  (episode-ui/route-page (episode-page nil))
        intro #(episode-ui/intro [:h1 "Episode 2"] (merge page %) nil nil nil nil true)]
    (is (= "Episode 2, 1 h, Explicit"
           (spoken (second (elements :p (intro {:numbering {:type "full" :episode 2} :published nil
                                                :explicit? true})))))
        "after the show, which is the heading's subtitle")
    (is (= [[:p.details-link [:a {:href "#details" :on {:click [[:event/prevent-default] [:page/show "details"]]}}
                              "Details" [:span {:aria-hidden "true"} " ↓"]]]]
           (elements :p.details-link (intro {})))
        "and a link to its Details")
    (is (empty? (elements :p.details-link (episode-ui/intro [:h1 "Episode 2"] page nil nil nil nil false)))
        "but none when it has no Details")
    (is (= "Support the show: Give, example.org"
           (text-of (last (elements :p (intro {:funding [{:url "https://pay.example/1" :content "Give"}
                                                         {:url "https://www.example.org/donate"}
                                                         {:url "javascript:alert(1)" :content "No"}]})))))
        "the links of the web alone, by their text or their host")))

(deftest the-details-of-an-episode
  (let [rows (into {} (episode-ui/details-rows {:link          "https://example.org/?p=2"
                                                :content-links [{:href "https://youtu.be/x" :text "Watch on YouTube"}]
                                                :license       {:content "CC-BY-4.0" :name "Creative Commons Attribution"
                                                                :version "4.0" :url "https://creativecommons.org/licenses/by/4.0/"}
                                                :enclosure     {:url "https://example.org/2.mp3" :length 86000000}}))]
    (is (= "example.org" (text-of (first (get rows "Web page")))))
    (is (= "Watch on YouTube" (text-of (first (get rows "Also on")))))
    (is (= "Creative Commons Attribution 4.0" (text-of (first (get rows "License")))))
    (is (= ["86 MB"] (get rows "File")))
    (is (= [nil] (get (into {} (episode-ui/details-rows {:enclosure {:length 0}})) "File"))
        "a length of 0 says that the feed doesn't know it"))
  (is (= ["850 kB" "86 MB" "1.5 GB"] (map episode-ui/size-text [850000 86000000 1500000000]))))

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
