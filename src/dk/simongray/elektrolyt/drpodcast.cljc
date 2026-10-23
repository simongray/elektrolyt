(ns dk.simongray.elektrolyt.drpodcast
  "The feeds of drpodcast.nu, which republishes the programmes of DR, the
  Danish broadcaster, from DR's radio API: the sibling of a feed of DR's
  own, whose episodes supplement it, and the series that DR publishes no
  podcast of.

  A feed of DR's own is at api.dr.dk/podcasts/v1/feeds/<slug>.xml. Its
  sibling is at drpodcast.nu/<slug>/feed.xml, under the same slug for all
  but a few series that DR renamed after drpodcast.nu first published
  them. Both feeds link their channel to DR's page of the series, whose
  URL ends in the series' production number, so the number tells whether
  two feeds are of one show. The slugs of the others are in drpodcast.nu's
  slugs.json, by DR's id of each series, with its production number.
  https://drpodcast.nu/
  https://github.com/drpodcastnu/DR1ommer"
  (:require [clojure.string :as str]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.wary-fetch.url :as url]))

(def slugs-url
  "The URL of drpodcast.nu's slugs, a JSON object of a :slug, a
  :productionNumber and a :title by DR's id of each series."
  "https://drpodcast.nu/slugs.json")

(def source
  "The id of drpodcast.nu among the directories that a search asks."
  :drpodcast)

(def source-name
  "The name of drpodcast.nu among the directories, for the listener."
  "drpodcast.nu")

;; Apple lists DR's feeds with queries such as ?format=podcast&limit=100,
;; which change neither the feed nor its sibling
(defn official-slug
  "The slug of DR's own feed at `url`, or nil for any other feed."
  [url]
  (when (= "api.dr.dk" (url/host url))
    (some->> (:path (url/parts url))
             (re-matches #"/podcasts/v1/feeds/([^/.]+)(?:\.xml)?")
             second)))

(defn feed-url
  "The URL of drpodcast.nu's feed of the series `slug`."
  [slug]
  (str "https://drpodcast.nu/" slug "/feed.xml"))

(defn official-url
  "The URL of DR's own feed of the series `slug`, if DR publishes one."
  [slug]
  (str "https://api.dr.dk/podcasts/v1/feeds/" slug ".xml"))

(def version
  "The version of the supplement of DR's own feeds, which each feed that
  it supplemented is marked with, so that one that an earlier version
  supplemented is fetched again."
  3)

(defn marked
  "The parsed `feed` of DR's own, marked as fetched with the episodes of
  drpodcast.nu, whether or not it found a sibling."
  [feed]
  (assoc feed ::supplemented version))

(defn fetched-as?
  "Whether the parsed `feed` at `url` is as a fetch gives it now, by
  whether DR's own feeds take the episodes of drpodcast.nu, `on?`: a feed
  of DR's own when it's marked by this version exactly when `on?`, and any
  other feed."
  [feed url on?]
  (or (nil? (official-slug url))
      (= (boolean on?) (= version (::supplemented feed)))))

(defn stale
  "The keys of the subscriptions of the `library` to DR's own feeds whose
  feeds a fetch would give otherwise now, by `on?`, as fetched-as? has it,
  e.g. those fetched before the listener turned it on or off."
  [library on?]
  (for [k     (keys (library/subscriptions library))
        :let  [{:keys [url feed]} (get-in library [:feeds k])]
        :when (and feed (not (fetched-as? feed url on?)))]
    k))

;; DR's feed of a series that it keeps in DR Lyd may hold a promo of less
;; than a minute, e.g. "Hør flere afsnit af denne i DR Lyd", which points
;; to the app rather than being an episode
(defn promo?
  "Whether the `item` of DR's own feed is a promo of DR Lyd: a title that
  ends in \"i DR Lyd\", and less than a minute long."
  [item]
  (let [title   (some-> (resolve/title item) str/trim str/lower-case)
        seconds (resolve/duration item)]
    (boolean (and title (str/ends-with? title "i dr lyd")
                  seconds (< seconds 60)))))

(defn without-promos
  "The parsed `feed` of DR's own without its promos of DR Lyd, as promo?
  tells them."
  [feed]
  (update feed :rss/items #(into [] (remove promo?) %)))

(defn production-number
  "The production number of the series of the parsed `feed`, which ends the
  URL of its channel's link, or nil."
  [feed]
  (some->> (:rss/link feed) str (re-find #"-(\d+)/?$") second))

(defn same-show?
  "Whether the parsed feeds `feed` and `other` are of one series."
  [feed other]
  (let [n (production-number feed)]
    (boolean (and n (= n (production-number other))))))

;; TODO: DR's series data names the official feed of each series as
;; podcastUrl, which DR1ommer reads but leaves out of slugs.json. With it
;; there, a feed of DR's own would find its sibling by its URL, without the
;; guess of the slug or the production number.
(defn slugs
  "The slugs of drpodcast.nu's feeds by the production number of each
  series, from the `slugs` that slugs.json has by DR's id of each."
  [slugs]
  (into {}
        (keep (fn [{:keys [slug productionNumber]}]
                (when (and (string? slug) (string? productionNumber))
                  [productionNumber slug])))
        (vals slugs)))

(defn matches
  "The series of the `slugs` of slugs.json whose titles have every word of
  `q`, whatever their case, by title."
  [slugs q]
  (let [words (str/split (str/lower-case (str/trim q)) #"\s+")
        has?  (fn [{:keys [title]}]
                (and (string? title)
                     (every? #(str/includes? (str/lower-case title) %) words)))]
    (sort-by (comp str/lower-case :title) (filter has? (vals slugs)))))

;; drpodcast.nu draws the artwork of each series from DR's colours and its
;; title, at image.jpg
(defn result
  "The result of a search for the `series` of slugs.json, a map of its
  :slug and :title, as dk.simongray.elektrolyt.search has results."
  [{:keys [slug title] :as series}]
  (let [url (feed-url slug)]
    {:url     url
     :guid    (guid/feed-guid nil url)
     :title   title
     :author  "DR"
     :artwork (str "https://drpodcast.nu/" slug "/image.jpg")}))

(comment
  (official-slug "https://api.dr.dk/podcasts/v1/feeds/genstart.xml?format=podcast")
  ;; => "genstart"
  (production-number {:rss/link "https://www.dr.dk/lyd/special-radio/genstart-2642056922000"})
  ;; => "2642056922000"
  #_.)
