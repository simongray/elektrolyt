(ns dk.simongray.podcast-app.ui.show-scenes
  "Scenes of the Show page as its feed arrives in stages, from what a search
  knew to the whole feed, with the Details of a show that says much of
  itself, and of the ways it can fail."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.inbox-scenes :as inbox-scenes]
            [dk.simongray.podcast-app.ui.search-scenes :as search-scenes]
            [portfolio.replicant :refer [defscene]]))

(def guid "917393e3-1b1e-5cef-ace4-edaa54e1f810")

(def url "https://feeds.podcastindex.org/pc20.xml")

(def known
  "What the search knew of the show."
  {:title         "Podcasting 2.0"
   :author        "Podcast Index LLC"
   :artwork       search-scenes/artwork
   :artwork-sizes []})

(defn rows
  "The rows of `n` episodes, newest first, every one of them a week apart."
  [n]
  (vec (for [i (range n)]
         {:guid      (str "ep" (- n i))
          :title     (str "Episode " (- n i) ": A Title of an Episode")
          :published (js/Date. (- 1792000000000 (* i 7 24 3600 1000)))
          :duration  (+ 3600 (* 137 i))})))

(defn show-page
  "The state of the Show page with the show's entry `shown` in :shows, and
  the `more` state merged in."
  ([shown]
   (show-page shown {}))
  ([shown more]
   (merge {:route    {:name         :route/show
                      :path-params  {:guid guid}
                      :query-params {:url url}}
           :library  (library/make "scenes")
           :searches {"pc20" {:status :done :results [(assoc known :guid guid :url url)]}}
           :shows    {guid shown}}
          more)))

(defscene what-the-search-knew
  "The moment after the click: the title, the author and the artwork that
  the search found, while the feed is on its way."
  (ui/page (show-page {:url url :status :loading})))

(defscene the-start-of-the-feed
  "The first episodes from the start of the feed, while the whole feed is
  fetched."
  (ui/page (show-page {:url    url
                       :status :loading
                       :page   (assoc known
                                      :notes [:p "The podcast about podcasting, with "
                                              [:a {:href "https://podcastindex.org"} "Podcast Index"] "."]
                                      :episodes (rows 20))})))

(defscene the-whole-feed
  "Every episode, of which the page lists 20 at a time."
  (ui/page (show-page {:url    url
                       :status :done
                       :shown  40
                       :page   (assoc known :episodes (rows 207))})))

(defscene new-episodes-that-wait
  "A feed from the cache that was fetched again while the page showed it,
  and brought new episodes, which wait for the button so that the list
  doesn't move by itself."
  (ui/page (show-page {:url    url
                       :status :done
                       :page   (assoc known :episodes (rows 207))
                       :newer  {:count 2}})))

(def pc20
  "A feed like that of Podcasting 2.0, with what it says of itself, where
  it's made and its license, and 20 episodes a week apart."
  {:rss/title         "Podcasting 2.0"
   :rss/link          "https://podcastindex.org"
   :rss/description   "<p>The podcast about podcasting, with Podcast Index.</p>"
   :rss/language      "en"
   :rss/copyright     "Podcast Index LLC 2026"
   :itunes/author     "Podcast Index LLC"
   :itunes/categories [{:text "News" :categories [{:text "Tech News"}]} {:text "Technology"}]
   :podcast/funding   [{:url "https://paypal.me/podcastindex" :content "Value 4 Value"}]
   :podcast/people    [{:content "Adam Curry" :role "host" :href "http://curry.com/"
                        :img "https://feeds.podcastindex.org/adam_avatar.jpg"}
                       {:content "Dave Jones" :role "host" :href "http://dave.sobr.org"
                        :img "https://feeds.podcastindex.org/dave_avatar.jpg"}]
   :podcast/locations [{:content "Austin, TX" :rel "creator" :osm {:type "R" :id 113314}}]
   :podcast/license   {:content "CC-BY-4.0"}
   :rss/items         (vec (for [{:keys [guid title published duration]} (rows 20)]
                             {:rss/guid        {:content guid}
                              :rss/title       title
                              :rss/pub-date    published
                              :itunes/duration duration}))})

(defscene a-show-with-its-details
  "A show that says much of itself: its Details beside the episodes where
  there's room for both, and below them on a phone, with a link to them at
  the top, and the people who make it."
  (ui/page (show-page {:url    url
                       :status :done
                       :page   (merge (show/feed->page url pc20)
                                      (select-keys known [:artwork :artwork-sizes]))})))

(defscene a-feed-that-failed
  "A fetch of the feed that failed, after what the search knew."
  (ui/page (show-page {:url    url
                       :status :failed
                       :error  {:message "The server answered 404."}})))

(defscene a-show-in-the-library
  "A show that the listener subscribes to, with the choice of where its new
  episodes go."
  (let [k (first (keys (:feeds inbox-scenes/lib)))]
    (ui/page {:route   {:name :route/show :path-params {:guid k} :query-params {}}
              :library inbox-scenes/lib})))

(defscene a-show-nobody-knows
  "A link to a show that the library doesn't keep and that names no feed."
  (ui/page {:route   {:name :route/show :path-params {:guid guid} :query-params {}}
            :library (library/make "scenes")}))
