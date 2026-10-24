(ns dk.simongray.elektrolyt.search.ui-scenes
  "Scenes of the Search page, from before a search to its results, with each
  state of their subscriptions."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.scenes :refer [artwork]]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

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
  "A show in the library among the results, marked before its title."
  (ui/page (search-page "podcasting"
                        (listed {:apple        (found results)
                                 :podcastindex (found [])
                                 :fyyd         (found [])})
                        {:library subscribed-library})))

(defscene directories-that-failed
  "A list from one directory, while the others didn't answer, which can
  be asked again."
  (ui/page (search-page "podcasting"
                        (listed {:apple        (found (take 1 results))
                                 :podcastindex (search/failed "podcastindex answered 500")
                                 :fyyd         (search/failed "timeout")}))))

(defscene a-directory-that-failed-with-a-kept-list
  "A directory that didn't answer, with the list that the cache kept of it
  less than a month ago, and its date."
  (ui/page (search-page "podcasting"
                        (listed {:apple        (found (take 1 results))
                                 :podcastindex (search/stale-list
                                                (search/failed "podcastindex answered 503")
                                                {:value {:results (vec (drop 1 results))}
                                                 :at    (js/Date. 1791800000000)})
                                 :fyyd         (found [])}))))

(defscene feeds-of-one-show
  "The feeds of one show at a link, e.g. in MP3 and in Opus, which share a
  title and so show their URLs."
  (ui/page (search-page "freakshow.fm"
                        {:status  :done
                         :results [{:url "https://freakshow.fm/feed/mp3" :title "Freak Show" :artwork artwork}
                                   {:url "https://freakshow.fm/feed/opus" :title "Freak Show" :artwork artwork}]})))

(defscene no-shows
  "Words that no directory has a show for."
  (ui/page (search-page "xyzzy" (listed {:apple (found []) :podcastindex (found []) :fyyd (found [])}))))

(defscene no-feeds-at-a-link
  "A link where no podcast feed was found."
  (ui/page (search-page "example.org" {:status :done :results []})))

(defscene library-not-yet-restored
  "Results before the library is restored, whose buttons wait for it."
  (ui/page (search-page "podcasting"
                        (listed {:apple (found results) :podcastindex (found []) :fyyd (found [])})
                        {:library nil})))
