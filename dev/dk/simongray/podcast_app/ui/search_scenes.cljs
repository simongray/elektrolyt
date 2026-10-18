(ns dk.simongray.podcast-app.ui.search-scenes
  "Scenes of the Search page, from before a search to its results, with each
  state of their subscriptions."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(def artwork
  "A grey square in place of a show's artwork, which needs no network."
  (str "data:image/svg+xml,"
       (js/encodeURIComponent
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><rect width='1' height='1' fill='#888'/></svg>")))

(def results
  "Shows as the directories give them: with artwork and without, and with a
  title long enough to wrap on a phone."
  [{:url     "https://feeds.podcastindex.org/pc20.xml"
    :title   "Podcasting 2.0"
    :author  "Podcast Index LLC"
    :artwork artwork}
   {:url     "https://example.org/long.xml"
    :title   "The Long-Running Show About Everything That Happens Under the Sun, Week by Week"
    :author  "A Network of Several Very Talkative Hosts"
    :artwork artwork}
   {:url    "https://example.org/plain.xml"
    :title  "A Show Without Artwork"
    :author "Someone"}])

(def subscribed-library
  "A library that is subscribed to the first of the results."
  (library/subscribe (library/make "scenes") (:url (first results))))

(defn search-page
  "The state of the Search page for the query `q` with the `search`, and
  the `more` state merged in."
  ([q search]
   (search-page q search {}))
  ([q search more]
   (merge {:route    {:name         :route/search
                      :path-params  {}
                      :query-params (if q {:q q} {})}
           :library  (library/make "scenes")
           :searches (when q {q search})}
          more)))

(defn listed
  "A search of the default directories, with the `lists` that have come,
  by source."
  [lists]
  (update (search/searching [:podcastindex :apple :fyyd]) :lists merge lists))

(defn found
  "The list of a directory that found the `shows`."
  [shows]
  {:status :done :results (vec shows)})

(defscene before-a-search
  "The page that the navigation links to, with an empty form, which says
  where it searches."
  (ui/page (search-page nil nil)))

(defscene searching
  "The list of the directory that has answered, while the others are yet
  to answer."
  (ui/page (search-page "podcasting" (listed {:apple (found (take 2 results))}))))

(defscene a-list-for-each-directory
  "What each directory found, side by side on a wide screen and one below
  the other on a phone, with the jump row above them."
  (ui/page (search-page "podcasting" (listed {:apple        (found results)
                                              :podcastindex (found (take 2 results))
                                              :fyyd         (found (take 1 results))}))))

(defscene results-and-subscriptions
  "Every state of a subscription: one subscribed, one under way, and one
  that failed."
  (ui/page (search-page "podcasting"
                        (listed {:apple        (found results)
                                 :podcastindex (found [])
                                 :fyyd         (found [])})
                        {:library          subscribed-library
                         :subscribing      #{(:url (second results))}
                         :subscribe-errors {(:url (last results))
                                            {:message "The server answered 404."}}})))

(defscene directories-that-failed
  "A list from one directory, while the others didn't answer, which can
  be asked again."
  (ui/page (search-page "podcasting"
                        (listed {:apple        (found (take 1 results))
                                 :podcastindex (search/failed "podcastindex answered 500")
                                 :fyyd         (search/failed "timeout")}))))

(defscene feeds-of-one-show
  "The feeds of one show at a link, e.g. in MP3 and in Opus, which share a
  title and so show their URLs."
  (ui/page (search-page "freakshow.fm"
                        {:status  :done
                         :results [{:url "https://freakshow.fm/feed/mp3" :title "Freak Show" :artwork artwork}
                                   {:url "https://freakshow.fm/feed/opus" :title "Freak Show" :artwork artwork}]
                         :failed  []})))

(defscene no-shows
  "Words that no directory has a show for."
  (ui/page (search-page "xyzzy" (listed {:apple (found []) :podcastindex (found []) :fyyd (found [])}))))

(defscene no-feeds-at-a-link
  "A link where no podcast feed was found."
  (ui/page (search-page "example.org" {:status :done :results [] :failed []})))

(defscene library-not-yet-restored
  "Results before the library is restored, whose buttons wait for it."
  (ui/page (search-page "podcasting"
                        (listed {:apple (found results) :podcastindex (found []) :fyyd (found [])})
                        {:library nil})))
