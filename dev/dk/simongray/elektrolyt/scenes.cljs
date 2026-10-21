(ns dk.simongray.elektrolyt.scenes
  "The fixtures that the scenes share: artwork that needs no network, and
  a show of four episodes a week apart, its feed, and a library that
  subscribes to it."
  (:require [dk.simongray.podcast.app.library :as library]))

(def artwork
  "A grey square in place of a show's artwork, which needs no network."
  (str "data:image/svg+xml,"
       (js/encodeURIComponent
        "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><rect width='1' height='1' fill='#888'/></svg>")))

(def url
  "The URL of the feed of the scenes' show."
  "https://feeds.podcastindex.org/pc20.xml")

(def feed
  "The feed of the scenes' show: four episodes a week apart."
  {:rss/title "Podcasting 2.0"
   :rss/items (vec (for [[i title] [[275 "Episode 275: \"Off The Rails\""]
                                    [274 "Episode 274: \"Vibe Cloning\""]
                                    [273 "Episode 273: \"Advertising is the Horse and Buggies of Podcasting\""]
                                    [272 "Episode 272: \"Is this Y'all?\""]]]
                     {:rss/title       title
                      :rss/guid        {:content (str "PC20-" i)}
                      :rss/pub-date    (js/Date. (- 1792000000000 (* (- 275 i) 7 24 3600 1000)))
                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                      :itunes/duration (+ 5400 (* 60 (- i 270)))}))})

(def lib
  "A library subscribed to the scenes' show."
  (library/subscribe (library/make "scenes") url feed))

(defn id
  "The id of the episode `i` of the scenes' show, e.g. 275."
  [i]
  [(first (keys (:feeds lib))) (str "PC20-" i)])
