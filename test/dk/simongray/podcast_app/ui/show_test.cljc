(ns dk.simongray.podcast-app.ui.show-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.ui.show :as show]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(deftest the-duration-of-an-episode
  (is (= "1 h 23 min" (show/duration-text 4980)))
  (is (= "2 h" (show/duration-text 7200)))
  (is (= "45 min" (show/duration-text 2700)))
  (is (= "1 min" (show/duration-text 40)) "rounded to the minute")
  (is (= "20 s" (show/duration-text 20)) "but for less than half a minute"))

(deftest the-status-of-the-episodes
  (is (= "Loading episodes…" (show/status-text :loading 20 nil nil)) "while the whole feed is fetched")
  (is (= "Loading episodes…" (show/status-text :opening 0 nil nil)) "while the cache is read")
  (is (= "207 episodes." (show/status-text :done 207 nil nil)))
  (is (= "1 episode." (show/status-text nil 1 nil nil)) "a show that the library keeps")
  (is (= "The feed couldn't be fetched. The server answered 404."
         (show/status-text :failed 0 {:message "The server answered 404."} nil)))
  (is (= "207 episodes. 2 new episodes are in." (show/status-text :done 207 nil 2)))
  (is (= "207 episodes. 1 new episode is in." (show/status-text :done 207 nil 1))))

(defn text-of
  "The text of the Hiccup `x`, as one string."
  [x]
  (apply str (filter string? (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x))))

(defn spoken
  "The text of the Hiccup `x` that a screen reader reads, as one string:
  all of it but what's aria-hidden."
  [x]
  (let [hidden? #(and (vector? %) (= "true" (:aria-hidden (second %))))]
    (->> (tree-seq #(and ((some-fn vector? seq?) %) (not (hidden? %)))
                   #(if (vector? %) (rest %) %)
                   x)
         (filter string?)
         (apply str))))

(deftest a-show-that-is-still-on-its-way
  (let [state #(hash-map :route {:name :route/show :path-params {:guid "g"} :query-params {}}
                         :shows {"g" {:status %}})]
    (is (= "Looking for the show in the Podcast Index…" (text-of (show/content (state :looking-up) nil))))
    (is (= "Loading the show…" (text-of (show/content (state :loading) nil)))
        "rather than say there's no such show")
    (is (= "Loading the show…" (text-of (show/content (state :opening) nil)))
        "nor while the cache is read")
    (is (re-find #"isn't in the library" (text-of (show/content (state :not-found) nil))))))

(deftest more-episodes-on-request
  (let [rows     (vec (for [i (range 45)] {:guid (str i) :title (str "Episode " i)}))
        href     #(str "/episodes/" (:guid %))
        section  (show/episodes "g" href rows :done 20 nil nil)
        [_ & xs] section
        items    (some #(when (and (vector? %) (= :ol.episodes (first %))) (last %)) xs)
        button   (some #(when (and (vector? %) (= :button (first %))) %) xs)]
    (is (= 20 (count items)))
    (is (= [:a {:href "/episodes/0"} "Episode 0"] (get-in (vec (first items)) [2 1 1]))
        "each title links to the page of its episode")
    (is (= [[:show/more "g" 45]] (get-in button [1 :on :click])))
    (is (nil? (some #(when (and (vector? %) (= :button (first %))) %)
                    (show/episodes "g" href rows :done 45 nil nil)))
        "no button once they're all listed")))

(deftest how-often-a-show-publishes
  (let [day (* 24 3600 1000)]
    (is (= ["More than once a day" "About every day" "About every 3 days" "About every week"
            "About every 2 weeks" "About every month" "About every 3 months"]
           (map #(show/cadence-text (* % day)) [0.25 1 3 7 14 30 91])))
    (is (= "Weekly" (show/schedule-text {:update-frequency "Weekly" :cadence (* 3 day)}))
        "the schedule that the show states first")
    (is (= "About every week" (show/schedule-text {:cadence (* 7 day)})))
    (is (nil? (show/schedule-text {:cadence (* 7 day) :complete? true})) "none once it's complete")))

(deftest the-facts-of-a-show
  (is (= ["Tech News" "Audiobook" "Complete" "Explicit" "Made with AI"]
         (show/facts {:categories [["News" "Tech News"] ["Technology"]] :medium "audiobook"
                      :complete? true :explicit? true :ai-content? true}))
      "the first category, or its subcategory, as Apple shows it")
  (is (every? nil? (show/facts {:medium "podcast" :explicit? false})) "no medium for a podcast")
  (let [rows (show/details-rows {:link       "https://www.podcastindex.org/"
                                 :categories [["News" "Tech News"] ["Technology"]]
                                 :serial?    true
                                 :locations  [{:content "Austin, TX" :rel "creator" :osm {:type "R" :id 113314}}
                                              {:content "Birmingham" :rel "subject"}
                                              {:content "Marlow" :rel "Creator"}]
                                 :copyright  "Podcast Index LLC 2026"}
                                "https://feeds.podcastindex.org/pc20.xml")]
    (is (= ["Website" "Categories" "Language" "Latest episode" "Schedule" "Order"
            "Made in" "Location" "License" "Copyright" "Feed"]
           (map first rows))
        "in the order of Apple's Information, with the places by what they are")
    (is (= "podcastindex.org" (text-of (first (second (first rows))))) "the website by its host")
    (is (= ["Tech News" "Technology"] (second (second rows))))
    (is (= "Austin, TXMarlow" (text-of (second (nth rows 6)))) "two places made in, under one name")
    (is (= "https://www.openstreetmap.org/relation/113314"
           (get-in (vec (first (second (nth rows 6)))) [1 :href]))
        "a place linked to its map")))

(deftest the-details-beside-the-episodes
  (let [state {:route {:name :route/show :path-params {:guid "g"} :query-params {:url "https://example.org/feed.xml"}}
               :shows {"g" {:url    "https://example.org/feed.xml"
                            :status :done
                            :page   {:title "A Show" :link "https://example.org" :explicit? true
                                     :people [{:name "Adam" :role "host" :img "https://example.org/adam.jpg"}]
                                     :episodes [{:guid "1" :title "One"}]}}}}
        x     (show/content state [:h1 "A Show"])
        [_ _ main aside] x
        header (first (elements :header main))]
    (is (= :article.with-details (first x)) "the show as an article")
    (is (= [:hgroup [:h1 "A Show"] nil] (second header)) "its header beginning with its heading")
    (is (= [:p "Explicit"] (first (elements :p header))))
    (is (= [[:p.details-link [:a {:href "#details" :on {:click [[:event/prevent-default] [:page/show "details"]]}}
                              "Details" [:span {:aria-hidden "true"} " ↓"]]]]
           (elements :p.details-link header))
        "a link to the Details, which a phone shows after the episodes")
    (is (= [:aside.details {:aria-labelledby "details"} [:h2.visually-hidden {:id "details" :tabindex -1} "Details"]]
           (subvec aside 0 3))
        "the Details last, labelled by their heading")
    (is (= [[:dt "Website"] [:dd [:a {:href "https://example.org" :target "_blank" :aria-describedby "new-tab-note"}
                                 "example.org"]]]
           (subvec (first (elements :div aside)) 2))
        "a name and its value in a div of the dl")
    (is (= "PeopleAdam, Host" (text-of (drop 4 aside)))))
  (testing "no Details, and no link to them, before the feed has any"
    (let [state {:route {:name :route/show :path-params {:guid "g"} :query-params {}}
                 :shows {"g" {:status :loading :page {:title "A Show" :episodes []}}}}
          [_ _ main aside] (show/content state [:h1 "A Show"])]
      (is (nil? aside))
      (is (empty? (elements :p.details-link main))))))

(deftest a-show-that-has-no-page-yet
  (is (= [[:h1 "Show"] [:p {:role "status"} "Loading the show…"]]
         (show/content {:route {:name :route/show :path-params {:guid "g"} :query-params {}}
                        :shows {"g" {:status :loading}}}
                       [:h1 "Show"]))
      "its heading, with no article around it"))

(deftest new-episodes-on-request
  (let [rows      [{:guid "1" :title "Episode 1"}]
        [_ & xs]  (show/episodes "g" #(str "/episodes/" (:guid %)) rows :done 20 nil 2)
        buttons   (filter #(and (vector? %) (= :button (first %))) xs)]
    (is (= [[[:show/newer "g"]] "Show the new episodes"]
           ((juxt #(get-in % [1 :on :click]) last) (first buttons)))
        "a button above the list, which stays as it is until it's pressed")
    (is (= 1 (count buttons)))))
