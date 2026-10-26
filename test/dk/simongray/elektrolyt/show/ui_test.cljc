(ns dk.simongray.elektrolyt.show.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.library.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.show.ui :as show]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui-test :refer [elements lang-of spoken text-of]]))

(def url
  "The URL of the feed of the show g of the tests."
  "https://example.org/feed.xml")

(defn show-page
  "The state of the Show page of the show g, whose link gives the URL of
  its feed, with the `more` state merged in."
  ([]
   (show-page {}))
  ([more]
   (merge {:route {:name :route/show :path-params {:guid "g"} :query-params {:url url}}}
          more)))

(defn fed
  "The feed of the show g of the tests, titled `title`, with the `more`
  properties and the episodes `items`."
  [title more & items]
  (merge {:rss/title title
          :rss/items (vec (for [[i t] (map-indexed vector items)]
                            {:rss/title t :rss/guid {:content (str (inc i))}}))}
         more))

(deftest the-status-of-the-episodes
  (is (= "Loading episodes…" (show/status-text :loading 20 nil nil))
      "while the whole feed is fetched, or the cache is read")
  (is (= "207 episodes." (show/status-text :done 207 nil nil)))
  (is (= "207 episodes." (show/status-text :checking 207 nil nil)) "and while it's fetched again")
  (is (= "The feed couldn't be fetched. Its server answered with an error, 404."
         (show/status-text :failed 0 {:id :fetch/status :status 404 :message "The feed stays as it was."} nil))
      "in the listener's words, not the library's")
  (is (= "The feed couldn't be fetched." (show/status-text :failed 0 {:id :fetch/other} nil)))
  (is (= "The feed couldn't be fetched. Its server answered with an error, 410. 207 episodes from before."
         (show/status-text :failed 207 {:id :fetch/status :status 410} nil))
      "with the episodes of the copy kept from before")
  (is (= "207 episodes. 2 new episodes are in." (show/status-text :done 207 nil 2)))
  (is (= "207 episodes. 1 new episode is in." (show/status-text :done 207 nil 1))))

(deftest a-show-that-is-still-on-its-way
  (let [unlinked {:route {:name :route/show :path-params {:guid "g"} :query-params {}}}]
    (is (= "Looking for the show in the Podcast Index…"
           (text-of (show/content (assoc unlinked :requests {[:look-up "g"] {}}) nil))))
    (is (= "Loading the show…" (text-of (show/content (show-page {:requests {[:whole url] {}}}) nil)))
        "rather than say there's no such show")
    (is (= "Loading the show…" (text-of (show/content (show-page {:requests {[:open url] {}}}) nil)))
        "nor while the cache is read")
    (let [x (show/content (show-page {:feeds {url {:error {:id :fetch/unreachable}}}}) nil)]
      (is (= "The feed couldn't be fetched. Its server couldn't be reached."
             (text-of (first (elements :p (elements :div.notice x)))))
          "in a notice")
      (is (= [[[:show/retry "g" url]] "main h1"]
             ((juxt (comp :click :on second) (comp :data-refocus second))
              (first (elements controls/button x))))
          "which fetches it again, and gives the focus to the heading as the button goes"))
    (is (re-find #"isn't in the library"
                 (text-of (show/content (assoc unlinked :feed-urls {"g" {:not-found? true}}) nil))))))

(deftest a-show-that-the-library-keeps
  (let [state  {:route          {:name :route/show :path-params {:guid inbox-test/k} :query-params {}}
                :library        inbox-test/lib
                :feeds          {inbox-test/url {:feed (fed "Old" {} "One") :whole? false}}
                :requests       {[:whole inbox-test/url] {}}
                :episodes-shown {inbox-test/k 20}}
        status (some #(when (= "status" (:role (second %))) (text-of %))
                     (elements :p (show/content state [:h1 "A Show"])))]
    (is (re-find #"^\d+ episodes?\.$" status)
        "lists its episodes from the library, whatever a load of the show before its subscription left")))

(deftest more-episodes-on-request
  (let [rows     (vec (for [i (range 45)] {:guid (str i) :title (str "Episode " i)}))
        page     {:episodes rows :episode-count 45}
        section  (show/episodes (show-page {:episodes-shown {"g" 20}}) page)
        [_ & xs] section
        items    (some #(when (and (vector? %) (= :ol.episodes (first %))) (last %)) xs)
        button   (some #(when (and (vector? %) (= :button (first %))) %) xs)
        [_ {:keys [href]} title] (first (elements :a (first items)))]
    (is (= 20 (count items)))
    (is (= "Episode 0" title))
    (is (re-find #"^/shows/g/episodes/\w+/episode-0\?url=" href)
        "each title links to the page of its episode, with the URL of the feed")
    (is (= [[:show/more "g" 45]] (get-in button [1 :on :click])))
    (is (= "ol.episodes > li:nth-child(21) :is(h3 a, h3[tabindex])" (get-in button [1 :data-refocus]))
        "which gives the focus to the first episode that it lists, once it goes, by its link or its title")
    (is (nil? (some #(when (and (vector? %) (= :button (first %))) %)
                    (show/episodes (show-page {:episodes-shown {"g" 45}}) page)))
        "no button once they're all listed")))

(deftest an-episode-without-a-guid
  (let [feed  {:rss/title "A Show" :rss/items [{:rss/title "Nameless"}]}
        state (show-page {:feeds {url {:feed feed :whole? true}}})
        [item] (elements :li (elements :ol.episodes (show/content state [:h1 "A Show"])))]
    (is (= 0 (get-in item [1 :replicant/key])) "keyed by its place in the list")
    (is (= "Nameless" (text-of (elements :h3 item))))
    (is (empty? (elements :a item))
        "but not linked, since an item with neither a guid nor an enclosure has no page")
    (is (= -1 (get-in (first (elements :h3 item)) [1 :tabindex]))
        "so its title takes the focus instead")))

(deftest an-episode-beside-its-artwork
  (let [src #(:src (second (first (elements :img (show/episode-row {} "/e" % nil {:artwork "https://example.org/show.jpg"})))))]
    (is (= "https://example.org/episode.jpg" (src {:title "One" :artwork "https://example.org/episode.jpg"})))
    (is (= "https://example.org/show.jpg" (src {:title "One"})) "or the show's, where it has none")))

(deftest an-episode-from-another-feed
  (let [facts #(first (elements :p (show/episode-row {} "/e" % nil {})))]
    (is (= "From drpodcast.nu" (spoken (facts {:title "One" :supplement "https://drpodcast.nu/genstart/feed.xml"}))))
    (is (nil? (facts {:title "One"})) "and no line for one of the show's own without facts")
    (is (= "Trailer" (spoken (facts {:title "One" :type "trailer"})))
        "a trailer says so, since it comes first in the list")
    (is (nil? (facts {:title "One" :type "full"})))))

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
  (is (= ["Audiobook" "Complete" "Explicit" "Made with AI"]
         (show/facts {:categories [["News" "Tech News"] ["Technology"]] :medium "audiobook"
                      :complete? true :explicit? true :ai-content? true}))
      "but not its categories, which are in the Details")
  (is (every? nil? (show/facts {:medium "podcast" :explicit? false})) "no medium for a podcast")
  (let [rows (show/details-rows {:link       "https://www.podcastindex.org/"
                                 :categories [["News" "Tech News"] ["Technology"]]
                                 :serial?    true
                                 :locations  [{:content "Austin, TX" :rel "creator" :osm {:type "R" :id 113314}}
                                              {:content "Birmingham" :rel "subject"}
                                              {:content "Marlow" :rel "Creator"}]
                                 :copyright  "Podcast Index LLC 2026"
                                 :funding    [{:url "https://paypal.me/podcastindex" :content "Value 4 Value"}]})]
    (is (= ["Support" "Categories" "Language" "Latest episode" "Schedule" "Order"
            "Made in" "Location" "License" "Copyright"]
           (map first rows))
        "in the order of Apple's Information, with the places by what they are")
    (is (= ["Value 4 Value"] (map text-of (second (first rows))))
        "the links that support the show, by their text")
    (is (= ["Tech News" "Technology"] (second (nth rows 1))))
    (is (= "Austin, TXMarlow" (text-of (second (nth rows 6)))) "two places made in, under one name")
    (is (= "https://www.openstreetmap.org/relation/113314"
           (get-in (vec (first (second (nth rows 6)))) [1 :href]))
        "a place linked to its map")))

(deftest the-line-under-the-title
  (let [page {:author "Podcast Index LLC" :link "https://podcastindex.org/" :explicit? true}
        line (show/byline page "https://feeds.podcastindex.org/pc20.xml")]
    (is (= "Podcast Index LLC, Explicit, Website, Feed" (spoken line)))
    (is (= ["https://podcastindex.org/" "https://feeds.podcastindex.org/pc20.xml"]
           (map #(:href (second %)) (elements :a line)))
        "its author as text, and links to its website and its feed")
    (is (= [links/new-tab-icon icons/rss]
           (map #(:icon (second (first (elements controls/icon %)))) (elements :a line)))
        "the feed's with the feed icon in place of the arrow of a new tab")
    (is (= "Explicit" (spoken (show/byline (dissoc page :author :link) nil)))
        "each where the show has it")))

(deftest the-details-beside-the-episodes
  (let [feed  (fed "A Show" {:rss/link        "https://example.org"
                             :itunes/explicit true
                             :rss/language    "en"
                             :podcast/people  [{:content "Adam" :img "https://example.org/adam.jpg"}]}
                   "One")
        state (show-page {:feeds {url {:feed feed :whole? true}}})
        x     (first (show/content state [:h1 "A Show"]))
        [_ _ main aside] x
        header (first (elements :header.show-intro main))
        [_ bookmark [_ heading line]] (second header)]
    (is (= :article.with-details (first x)) "the show as an article")
    (is (= ["bookmark" [:h1 "A Show"]] [(:class (second bookmark)) heading])
        "its header beginning with its bookmark and heading")
    (is (= ["https://example.org" "https://example.org/feed.xml"] (map #(:href (second %)) (elements :a line)))
        "and its byline below it, which links to its website and its feed")
    (is (= [:aside.details {:aria-labelledby "details"} [:h2.visually-hidden {:id "details"} "Details"]]
           (subvec aside 0 3))
        "the Details last, labelled by their heading")
    (is (= [[:dt "Language"] [:dd "English"]]
           (subvec (first (elements :div aside)) 2))
        "a name and its value in a div of the dl")
    (is (= "PeopleAdam, Host" (text-of (last (elements :div aside))))
        "and the people last, a value for each"))
  (testing "no Details before the feed has any"
    (let [state (show-page {:searches {"q" {:results [{:guid "g" :url url :title "A Show"}]}}
                            :requests {[:whole url] {}}})
          [_ _ _ aside] (first (show/content state [:h1 "A Show"]))]
      (is (nil? aside)))))

(defn show-in
  "The Show page of a show whose feed names the `language`, with its notes
  and an episode, under its heading."
  [language]
  (let [feed  (fed "Sprogets verden"
                   (cond-> {:rss/description "<p>Et ugentligt program om det danske sprog.</p>"}
                     language (assoc :rss/language language))
                   "Hvorfor siger vi hyggeligt?")
        state (show-page {:feeds {url {:feed feed :whole? true}}})]
    (show/content state (ui/page-heading state))))

(deftest a-show-in-its-own-language
  (let [x (show-in "da")]
    (is (= "da" (lang-of :h1 x)) "its title")
    (is (= "da" (lang-of :div.show-notes x)) "its notes, which the browser hyphenates by it")
    (is (= "da" (lang-of :a (elements :h3 x))) "the titles of its episodes"))
  (let [x (show-in nil)]
    (is (every? nil? (map #(lang-of % x) [:h1 :div.show-notes]))
        "none where the feed names no language, so the page's own applies")
    (is (nil? (lang-of :a (elements :h3 x))))))

(deftest a-subscription-whose-feed-fails
  (let [lib   (library/update-profile (library/make "test") profile/subscribe "g" {:url url :title "Gone"})
        state {:route   {:name :route/show :path-params {:guid "g"} :query-params {}}
               :library lib
               :feeds   {url {:error {:id :fetch/unreachable}}}}
        x     (show/content state [:h1 "Gone"])]
    (is (re-find #"^The feed couldn't be fetched\." (text-of (first (elements :p x)))))
    (is (= ["Try again to fetch the feed" "Unsubscribe from Gone"]
           (map spoken (concat (elements controls/button x) (elements controls/icon-button x))))
        "and the listener can still leave it")
    (is (= "Gone" (ui/page-title state)) "named by the subscription")))

(deftest a-subscribed-show-among-the-cards
  (let [lib          (library/update-profile (library/make "test") profile/subscribe "g" {:url url :title "Kept"})
        x            (show/show-cards {:library lib} [{:guid "g" :title "Kept" :url url}
                                                      {:guid "h" :title "Other" :url "https://example.org/other.xml"}])
        [kept other] (elements :h3 x)]
    (is (= " Kept, subscribed" (spoken kept)) "marked before its title, which a screen reader hears after it")
    (is (= [controls/icon {:icon icons/bookmark-simple-fill :class "bookmark" :title "Subscribed"}]
           (first (second kept)))
        "the filled bookmark among the title's words")
    (is (= "Other" (spoken other)))))

(deftest a-show-that-has-no-page-yet
  (is (= [[:h1 "Show"] [[:p.busy {:role "status"} "Loading the show…"] show/skeleton-rows]]
         (show/content (show-page {:requests {[:whole url] {}}}) [:h1 "Show"]))
      "its heading, with no article around it, over a line that moves while it waits and rows in the place of the episodes")
  (is (= "true" (get-in show/skeleton-rows [1 :aria-hidden])) "which a screen reader skips"))

(deftest new-episodes-on-request
  (let [rows      [{:guid "1" :title "Episode 1"}]
        state     (show-page {:feeds {url {:feed (fed "A Show" {} "Episode 1") :whole? true :newer {:count 2}}}})
        [_ & xs]  (show/episodes state {:episodes rows :episode-count 1})
        buttons   (filter #(and (vector? %) (= :button (first %))) xs)]
    (is (= [[[:show/newer "g"]] "Show the new episodes"]
           ((juxt #(get-in % [1 :on :click]) last) (first buttons)))
        "a button above the list, which stays as it is until it's pressed")
    (is (= "#episodes" (get-in (first buttons) [1 :data-refocus]))
        "and then gives the focus to the list's heading")
    (is (= 1 (count buttons)))))
