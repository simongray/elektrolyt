(ns dk.simongray.elektrolyt.episode.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.episode :as episode]
            [dk.simongray.elektrolyt.episode-test :as episode-test]
            [dk.simongray.elektrolyt.episode.ui :as episode-ui]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui-test :refer [elements lang-of nodes spoken text-of]]))

(defn episode-page
  "The state of the Episode page of the second episode of the test's feed,
  as the Show page loaded it, at the query `t`, with the `more` state
  merged in."
  ([t]
   (episode-page t {}))
  ([t more]
   (-> (episode-test/loaded false)
       (assoc :route {:name         :route/episode
                      :path-params  {:guid episode-test/g :hash episode-test/hash-2}
                      :query-params (cond-> {:url episode-test/url} t (assoc :t t))})
       (merge more))))

(deftest the-chapters-on-request
  (let [page   (episode-ui/route-page (episode-page nil))
        closed (episode-ui/chapters {} page [{:start-time 0 :title "Intro"}])
        [_ attrs] (first (elements :button.disclosure closed))]
    (is (empty? (elements :li closed)) "closed at first")
    (is (= ["false" "chapters-content"] ((juxt :aria-expanded :aria-controls) attrs)))
    (is (= [[:store/assoc-in [:open [:chapters (:id page)]] true]] (get-in attrs [:on :click]))
        "until the button of its heading opens them")))

(deftest the-chapters-and-their-links
  (let [page  (assoc (episode-ui/route-page (episode-page nil)) :language "de")
        items (->> (episode-ui/chapters {:open {[:chapters (:id page)] true}} page
                                        [{:start-time 0 :title "Intro" :url "https://example.org/intro"}
                                         {:start-time 60 :title "Sneaky" :url "javascript:alert(1)"}
                                         {:start-time 90 :url "https://example.org/untitled"}
                                         {:start-time 120 :title "Plain"}])
                   (elements :li))]
    (is (= [(links/new-tab [:a {:href "https://example.org/intro" :lang "de"} "Intro"])
            [:span {:lang "de"} "Sneaky"]
            (links/new-tab [:a {:href "https://example.org/untitled" :lang "de"} "https://example.org/untitled"])
            [:span {:lang "de"} "Plain"]]
           (map last items))
        "a link to the web alone, in a new tab, the URL of a chapter without a title, and the feed's language")
    (is (nil? (lang-of :a (first items))) "but not on the time, which is the page's")))

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
    (is (= [controls/button icons/play] ((juxt first (comp :icon second)) (button nil nil nil)))
        "with the player's icon before its words")
    (is (= "Play from 1:13" (text-of (button nil nil 73))) "from the time of the link")
    (is (= [[:player/play (episode/playable page) 73]]
           (get-in (button nil nil 73) [1 :on :click])))
    (is (= "Resume" (text-of (button nil {:position 600} nil)))
        "where the listener left it")
    (is (= "Play" (text-of (button nil {:position 600 :played? true} nil))) "unless it's played")
    (is (= "Resume" (text-of (button (playing (:id page) false) {:position 600} nil)))
        "while it's paused")
    (is (= [[:player/pause]] (get-in (button (playing (:id page) true) nil nil) [1 :on :click]))
        "and Pause while it plays, with no aria-pressed")
    (is (= ["Pause" icons/pause] ((juxt text-of (comp :icon second)) (button (playing (:id page) true) nil nil))))
    (is (= "Play" (text-of (button (playing ["other" "x"] true) nil nil))) "while another one plays")
    (let [there {:id        ["other" "x"]
                 :media     {:paused true}
                 :elsewhere {:id (:id page) :paused false :current-time 100 :at 0}
                 :now       1000}]
      (is (= [[:player/pause]] (get-in (button there nil nil) [1 :on :click]))
          "and Pause while another tab plays it, as the player says")
      (is (= "Play" (text-of (button (assoc-in there [:elsewhere :paused] true) nil nil)))
          "but not once that tab paused it"))))

(deftest the-time-it-has-left
  (is (= "1h" (text-of (episode-ui/time-left 3600 nil))) "its length at first")
  (is (= "50m left" (text-of (episode-ui/time-left 3600 {:position 600})))
      "and what's left of it once it's started, as a fact rather than in Resume")
  (is (= "1h" (text-of (episode-ui/time-left 3600 {:position 600 :played? true}))) "but not once it's played")
  (is (nil? (episode-ui/time-left nil {:position 600})) "and nothing without a length"))

(deftest the-buttons-that-queue-it
  (let [page    (episode-ui/route-page (episode-page nil))
        texts   #(map text-of (episode-ui/queue-buttons page %))]
    (is (= ["Play next" "Play last"] (texts nil)))
    (is (= ["Play next" "Remove from queue"] (texts :queued)))
    (is (empty? (texts :current)) "none for the episode that plays")
    (is (= [[:queue/add (episode/playable page) (:title page) true]]
           (get-in (first (episode-ui/queue-buttons page nil)) [1 :on :click])))
    (is (= [icons/rows-plus-top icons/rows-plus-bottom icons/minus]
           (map (comp :icon second) (concat (episode-ui/queue-buttons page nil)
                                            (rest (episode-ui/queue-buttons page :queued)))))
        "with icons that add a row at the top or the bottom, or take one away")
    (is (= (controls/single-click [[:queue/remove (:id page)]])
           (get-in (second (episode-ui/queue-buttons page :queued)) [1 :on :click]))
        "Remove once for a double click on Add, which turns into it")))

(deftest the-page-of-an-episode
  (let [x (ui/page (episode-page nil))]
    (is (= "Episode 2: The Present – Elektrolyt" (ui/document-title (episode-page nil))))
    (is (= ["h1" "h2" "h3" "h2" "h2"]
           (keep #(when (vector? %) (re-find #"^h[1-6]" (name (first %))))
                 (nodes (elements :main#main x))))
        "the title, Show notes, the notes' own heading below it, the Details and the picks")
    (let [[article] (elements :article.with-details x)]
      (is (= (pr-str [episode-test/g "https://example.org/?p=2"]) (:replicant/key (second article)))
          "the episode as an article, with a key of its own, so that its files are read when it mounts")
      (is (= [[:aside.details {:aria-labelledby "details"}] [:section {:aria-labelledby "episode-picks" :class "picks"}]]
             (map #(subvec % 0 2) (take-last 2 article)))
          "the Details after the show notes, and the picks after them")
      (let [[header] (elements :header.episode-intro article)]
        (is (= ["Episode 2: The Present" "A Show"] (map text-of (rest (second header))))
            "its header beginning with its heading, and its show as the subtitle")))))

(deftest an-episode-in-its-shows-language
  (let [x (ui/page (assoc-in (episode-page nil) [:feeds episode-test/url :feed :rss/language] "de"))]
    (is (= "de" (lang-of :h1 x)) "its title")
    (is (= "de" (lang-of :a (elements :hgroup x))) "its show's title")
    (is (= "de" (lang-of :div.show-notes x)) "its notes, which the browser hyphenates by it"))
  (let [x (ui/page (episode-page nil))]
    (is (every? nil? (map #(lang-of % x) [:h1 :div.show-notes]))
        "none where the feed names no language, so the page's own applies")))

(deftest the-numbers-of-an-episode
  (is (= ["Season 2" "Episode 3" nil] (episode-ui/numbering-facts {:type "full" :season 2 :episode 3})))
  (is (= ["The Sequel" "Ch.3" "Bonus"]
         (episode-ui/numbering-facts {:type "bonus" :season 2 :season-name "The Sequel"
                                      :episode 204 :episode-display "Ch.3"}))
      "a season's name and an episode's display text in place of their numbers")
  (is (= [nil "Episode 3.5" "Trailer"] (episode-ui/numbering-facts {:type "trailer" :episode 3.5})))
  (is (= "3" (episode-ui/number-text 3.0))))

(deftest the-facts-of-an-episode
  (let [state (episode-page nil)
        page  (episode-ui/route-page state)
        intro #(episode-ui/intro state [:h1 "Episode 2"] (merge page %))]
    (is (= "Episode 2, 1h, Explicit"
           (spoken (second (elements :p (intro {:numbering {:type "full" :episode 2} :published nil
                                                :explicit? true})))))
        "after the show, which is the heading's subtitle")
    (is (= "Episode 2, 1h, From drpodcast.nu"
           (spoken (second (elements :p (intro {:numbering  {:type "full" :episode 2} :published nil
                                                :supplement "https://drpodcast.nu/genstart/feed.xml"})))))
        "and the feed it came from when that isn't its show's own")))

(deftest the-source-of-an-episode
  (let [rows (into {} (episode-ui/details-rows {:supplement "https://drpodcast.nu/genstart/feed.xml"}))]
    (is (= "drpodcast.nu" (text-of (into [:p] (get rows "Source"))))
        "in the Details, as a link to its site"))
  (is (nil? (seq (keep identity (get (into {} (episode-ui/details-rows {})) "Source"))))
      "and none for an episode of the show's own feed"))

(deftest the-links-that-support-the-show
  (let [rows (into {} (episode-ui/details-rows {:funding [{:url "https://pay.example/1" :content "Give"}
                                                          {:url "https://www.example.org/donate"}
                                                          {:url "javascript:alert(1)" :content "No"}]}))]
    (is (= ["Give" "example.org"] (map text-of (get rows "Support")))
        "in the Details, the links of the web alone, by their text or their host")))

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
        "a length of 0 says that the feed doesn't know it")))

(deftest the-transcript-on-request
  (let [page  (episode-ui/route-page (episode-page nil))
        doc   {:segments [{:start-time 0.0 :speaker "Adam" :body "Hello."}]}
        open  {:open {[:transcript (:id page)] true}}
        shown #(text-of (episode-ui/transcript open page %))
        closed (episode-ui/transcript {} page nil)
        button #(second (first (elements :button.disclosure %)))]
    (is (= "Transcript" (text-of closed)) "closed, and not read yet")
    (is (= ["false" "transcript-content"] ((juxt :aria-expanded :aria-controls) (button closed)))
        "the button of its heading")
    (is (= [[:store/assoc-in [:open [:transcript (:id page)]] true]]
           (get-in (button closed) [:on :click]))
        "which opens it")
    (is (= [[:store/assoc-in [:open [:transcript (:id page)]] false]]
           (get-in (button (episode-ui/transcript open page nil)) [:on :click]))
        "or closes it")
    (is (= [[:episode/read-transcript (:id page) (:view page)]]
           (-> (elements :p (episode-ui/transcript open page nil)) first second :replicant/on-mount))
        "and an open one that isn't read reads it as it shows, e.g. after the state forgot it")
    (is (= "TranscriptLoading the transcript…" (shown nil)))
    (is (= "TranscriptLoading the transcript…" (shown {:transcript-status :reading})))
    (is (= "Transcript0:00 Adam: Hello."
           (shown {:transcript-status :read :transcript {:document doc}})))
    (is (= "TranscriptThe transcript couldn't be read."
           (shown {:transcript-status :read :transcript {:error {:message "404"}}})))))

(deftest a-transcript-in-its-own-language
  (let [page (assoc (episode-ui/route-page (episode-page nil)) :language "de")
        doc  {:segments [{:start-time 0.0 :speaker "Ana" :body "Hola."}]}
        lang #(lang-of :div.transcript
                       (episode-ui/transcript {:open {[:transcript (:id %1)] true}} %1
                                              {:transcript-status :read
                                               :transcript        {:document doc :language %2}}))]
    (is (= "es" (lang page "es")) "the language that its tag names, rather than the feed's")
    (is (= "de" (lang page nil)) "or else the feed's")
    (is (nil? (lang (dissoc page :language) nil)) "and none where neither names one")))

(deftest an-episode-that-isnt-there
  (testing "while the feed is on its way"
    (is (= "Loading the episode…" (text-of (episode-ui/missing (episode-page nil))))))
  (testing "once the whole feed doesn't have it"
    (let [state (assoc (episode-test/loaded true)
                       :route {:name         :route/episode
                               :path-params  {:guid episode-test/g :hash "0000000"}
                               :query-params {:url episode-test/url}})
          x     (episode-ui/missing state)]
      (is (re-find #"isn't in its show's feed" (text-of x)))
      (is (= [(str "/shows/" episode-test/g "?url=https%3A%2F%2Fexample.org%2Ffeed.xml")]
             (map #(:href (second %)) (elements :a x)))
          "with a link to the show"))))
