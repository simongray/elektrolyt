(ns dk.simongray.elektrolyt.library.ui-scenes
  "Scenes of the Library page: the start of the Inbox, the shows in each
  layout, a refresh under way, and a library with no shows yet."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.scenes :refer [artwork]]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defn feed
  "A feed titled `title` with `n` episodes, the newest on `newest`."
  [title n newest]
  {:rss/title    title
   :itunes/image {:href artwork}
   :rss/items    (vec (for [i (range n)]
                        {:rss/title    (str title ", episode " (- n i))
                         :rss/guid     {:content (str title "-" i)}
                         :rss/pub-date (js/Date. (- (inst-ms newest) (* i 7 24 3600 1000)))}))})

(def lib
  "A library subscribed to four shows, one with a title long enough to
  wrap."
  (reduce (fn [lib [i title n newest]]
            (library/subscribe lib (str "https://example.org/" i ".xml") (feed title n newest)))
          (library/make "scenes")
          [[1 "Podcasting 2.0" 12 #inst "2026-10-16"]
           [2 "The Daily" 30 #inst "2026-10-17"]
           [3 "A Show With a Rather Long Title That Wraps on a Phone" 3 #inst "2026-09-02"]
           [4 "Hardcore History" 8 #inst "2026-06-11"]]))

(defn library-page
  "The state of the Library page, with the `more` state merged in."
  [more]
  (merge {:route {:name :route/library} :library lib} more))

(defscene the-library
  "The shows, the newest episode first, as a list on a phone and a grid
  wider than that."
  (ui/page (library-page {})))

(defscene as-a-list
  "The listener's choice of a list at every width, by title."
  (ui/page (library-page {:library-view {:layout :list :sort :title}})))

(defscene as-a-grid
  "The listener's choice of a grid at every width."
  (ui/page (library-page {:library-view {:layout :grid}})))

(defscene while-it-refreshes
  "A refresh under way, with the list marked busy."
  (ui/page (library-page {:refresh-status {:running? true :done 12 :total 40}})))

(defscene no-shows-yet
  "A library with no subscriptions."
  (ui/page {:route {:name :route/library} :library (library/make "scenes")}))
