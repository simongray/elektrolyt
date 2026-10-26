(ns dk.simongray.elektrolyt.show.ui-scenes
  "Scenes of the Show page, as its feed arrives in stages, and in the ways
  it can fail."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.scenes :refer [artwork lib url]]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(def guid
  "The guid that the feed of the scenes' show declares."
  "917393e3-1b1e-5cef-ace4-edaa54e1f810")

(def known
  "What the search found of the show."
  {:title         "Podcasting 2.0"
   :author        "Podcast Index LLC"
   :artwork       artwork
   :artwork-sizes []})

(defn rows
  "The rows of `n` episodes, newest first, every one of them a week apart."
  [n]
  (vec (for [i (range n)]
         {:guid      (str "ep" (- n i))
          :title     (str "Episode " (- n i) ": A Title of an Episode")
          :published (js/Date. (- 1792000000000 (* i 7 24 3600 1000)))
          :duration  (+ 3600 (* 137 i))})))

(defn items
  "The items of a feed of `n` episodes, newest first, every one of them a
  week apart, as rows has them."
  [n]
  (vec (for [{:keys [guid title published duration]} (rows n)]
         {:rss/guid        {:content guid}
          :rss/title       title
          :rss/pub-date    published
          :itunes/duration duration})))

(def podcasting
  "The feed of the scenes' show, with what the search found and no
  episodes yet."
  {:rss/title     "Podcasting 2.0"
   :itunes/author "Podcast Index LLC"
   :itunes/image  {:href artwork}
   :rss/items     []})

(defn show-page
  "The state of the Show page of the scenes' show, with the `more` state
  merged in, e.g. its :feeds and :requests."
  [more]
  (merge {:route    {:name         :route/show
                     :path-params  {:guid guid}
                     :query-params {:url url}}
          :library  (library/make "scenes")
          :searches {"pc20" {:status :done :results [(assoc known :guid guid :url url)]}}}
         more))

(defn whole
  "The state of the Show page with the whole `feed`, and the `more` state
  merged in."
  ([feed]
   (whole feed {}))
  ([feed more]
   (show-page (merge {:feeds {url {:feed feed :whole? true}}} more))))

(defscene what-the-search-knew
  "The moment after the click: the title, the author and the artwork that
  the search found, while the feed is on its way."
  (ui/page (show-page {:requests {[:whole url] {}}})))

(defscene the-start-of-the-feed
  "The first episodes from the start of the feed, while the whole feed is
  fetched."
  (ui/page (show-page {:requests {[:whole url] {}}
                       :feeds    {url {:feed   (assoc podcasting
                                                       :rss/description (str "<p>The podcast about podcasting, with "
                                                                             "<a href=\"https://podcastindex.org\">Podcast Index</a>.</p>")
                                                       :rss/items (items 20))
                                       :whole? false}}})))

(defscene the-whole-feed
  "Every episode, of which the page lists 20 at a time."
  (ui/page (whole (assoc podcasting :rss/items (items 207)) {:episodes-shown {guid 40}})))

(defscene new-episodes-that-wait
  "A feed from the cache that was fetched again while the page showed it,
  and brought new episodes, which wait for the button so that the list
  doesn't move by itself."
  (ui/page (show-page {:feeds {url {:feed   (assoc podcasting :rss/items (items 207))
                                    :whole? true
                                    :newer  {:count 2}}}})))

(def pc20
  "A feed like that of Podcasting 2.0, with what it says of itself, where
  it's made and its licence, and 20 episodes a week apart."
  {:rss/title         "Podcasting 2.0"
   :rss/link          "https://podcastindex.org"
   :rss/description   "<p>The podcast about podcasting, with Podcast Index.</p>"
   :rss/language      "en"
   :rss/copyright     "Podcast Index LLC 2026"
   :itunes/author     "Podcast Index LLC"
   :itunes/image      {:href artwork}
   :itunes/categories [{:text "News" :categories [{:text "Tech News"}]} {:text "Technology"}]
   :podcast/funding   [{:url "https://paypal.me/podcastindex" :content "Value 4 Value"}]
   :podcast/people    [{:content "Adam Curry" :role "host" :href "http://curry.com/"
                        :img "https://feeds.podcastindex.org/adam_avatar.jpg"}
                       {:content "Dave Jones" :role "host" :href "http://dave.sobr.org"
                        :img "https://feeds.podcastindex.org/dave_avatar.jpg"}]
   :podcast/locations [{:content "Austin, TX" :rel "creator" :osm {:type "R" :id 113314}}]
   :podcast/license   {:content "CC-BY-4.0"}
   :rss/items         (items 20)})

(defscene a-show-with-its-details
  "A show that says much of itself: its Details beside the episodes where
  there's room for both, and below them on a phone, and the people who
  make it."
  (ui/page (whole pc20)))

(def podnews
  "The feed of a show that the scenes' show recommends."
  {:rss/title    "Podnews Weekly Review"
   :itunes/image {:href artwork}
   :rss/items    []})

(defscene shows-it-recommends
  "A show that recommends others in its podroll, one of which is in the
  library already, which its bookmark marks."
  (ui/page (whole (assoc pc20 :podcast/podroll {:remote-items [{:feed-guid "g1"} {:feed-guid "g2"}]})
                  {:library (library/subscribe (library/make "scenes") "https://example.org/weekly.xml" podnews)
                   :linked  {guid {:podroll [{:guid    "g1"
                                              :title   "Podnews Weekly Review"
                                              :url     "https://example.org/weekly.xml"
                                              :artwork artwork}
                                             {:guid "g2" :title "Boostagram Ball" :artwork artwork}]}}})))

(defscene while-it-unsubscribes
  "A show in the library in the frame after Unsubscribe is pressed, before
  the library lets it go: the empty bookmark, which can't be pressed yet,
  over its episodes."
  (ui/page (show-page {:library       (library/subscribe (library/make "scenes") url pc20)
                       :unsubscribing #{url}})))

(defscene in-the-colours-of-its-artwork
  "A show whose page takes the colours of its artwork, once they're worked
  out, here of a deep blue: its links and buttons, and a faint tint from
  the right."
  (ui/page (whole pc20 {:colors {artwork {:seed "#1d4e89" :dominant "#888888"}}})))

(def danish
  "A feed in Danish, with long compound words in its notes and five
  episodes a week apart."
  {:rss/title       "Sprogets verden"
   :rss/language    "da"
   :itunes/image    {:href artwork}
   :rss/description (str "<p>Et ugentligt program om det danske sprog: hvor ordene kommer fra, "
                         "hvordan de skifter betydning, og hvorfor sammensatte ord som "
                         "folketingsvalgkampagnerne og rigsfællesskabsforhandlingerne kan "
                         "blive så lange.</p>"
                         "<p>Hver uge tager værterne et ord under behandling, fra dets første "
                         "optræden i ordbøgerne til den måde, det bruges på i dag.</p>")
   :itunes/author   "Ord og mennesker"
   :rss/items       (vec (map (fn [{:keys [guid published duration]} title]
                                {:rss/guid        {:content guid}
                                 :rss/title       title
                                 :rss/pub-date    published
                                 :itunes/duration duration})
                              (rows 5)
                              ["Hvorfor siger vi hyggeligt?"
                               "Sammensatte ord og særskrivning"
                               "Dialekterne, der forsvinder"
                               "Låneord fra engelsk"
                               "Retskrivningsreformen, der aldrig kom"]))})

(defscene a-show-in-another-language
  "A show whose feed is in Danish: its title, its notes and the titles of
  its episodes have the feed's language, so that the browser hyphenates
  them by Danish rules and a screen reader reads them in Danish, while the
  app's own words stay English."
  (ui/page (whole danish)))

(defscene a-feed-that-failed
  "A fetch of the feed that failed, after what the search knew."
  (ui/page (show-page {:feeds {url {:error {:message "The server answered 404."}}}})))

(defscene a-show-in-the-library
  "A show that the listener subscribes to."
  (let [k (first (keys (:feeds lib)))]
    (ui/page {:route   {:name :route/show :path-params {:guid k} :query-params {}}
              :library lib})))

(defscene a-show-nobody-knows
  "A link to a show that the library doesn't keep and that names no feed."
  (ui/page {:route   {:name :route/show :path-params {:guid guid} :query-params {}}
            :library (library/make "scenes")}))
