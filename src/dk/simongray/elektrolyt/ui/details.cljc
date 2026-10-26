(ns dk.simongray.elektrolyt.ui.details
  "The Details of a show or an episode: its metadata, e.g. the links that
  support the show and the people who make it. Rows without a value are
  left out, and so are the Details when none of them has one."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.wary-fetch.url :as url]))

(def id
  "The id of the heading of the Details, which labels them."
  "details")

(defn host-text
  "The domain of the web URL `s` without www., or nil when `s` isn't a web
  URL."
  [s]
  (when (url/web-url? s)
    (str/replace (url/host s) #"^www\." "")))

(defn web-link
  "A link to the web URL `s` that opens in a new tab and shows the `text`,
  or else the URL's domain, with the `icon` after it, or nil when `s`
  isn't a web URL."
  ([s text]
   (web-link s text links/new-tab-icon))
  ([s text icon]
   (when-let [host (host-text s)]
     (links/new-tab [:a {:href s} (or (some-> text str/trim not-empty) host)] icon))))

;; podcast namespace, funding: the text is "to be displayed in the app next
;; to the link", and here it's the link's own text
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/funding.md
(defn support
  "The links of the `funding`, each with the feed's text for it."
  [funding]
  (keep #(some-> (web-link (:url %) (:content %))
                 (assoc-in [1 :replicant/key] (:url %)))
        funding))

(defn license
  "The `license`, linked to its terms where there's a URL: the name and
  version of a licence on the podcast namespace's list, or else its
  identifier."
  [{:keys [content name version url] :as license}]
  (when-let [text (if name
                    (str/join " " (remove nil? [name version]))
                    (some-> content str/trim not-empty))]
    (or (web-link url text) text)))

;; podcast namespace, location: rel creator is where the content was made,
;; and subject, the default, what it's about. The node value is for display.
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/location.md
(defn location-rows
  "The rows of the `locations`: Made in for where the content was made, and
  Location for what it's about, each place linked to a map where the feed
  gives its position."
  [locations]
  (let [label #(if (= "creator" (some-> (:rel %) str/lower-case)) "Made in" "Location")
        place #(when-let [place (some-> (:content %) str/trim not-empty)]
                 (or (web-link (resolve/location-url %) place) place))]
    (for [[k places] (group-by label locations)]
      [k (map place places)])))

;; W3C, Images tutorial, decorative images: the name beside a picture says
;; who it is, so its alt is empty. The picture is often a large photo, so
;; it comes from the server's thumbnails, as artwork does.
(defn person
  "A `person` who makes the show or the episode: a picture, the name,
  linked to their page where there's one, and the role."
  [{:keys [name role img href] :as person}]
  [:span.person
   (when (url/web-url? img)
     (artwork/img {:artwork img} 48 {:loading "lazy"}))
   [:span (or (web-link href name) name)
    (when role (str ", " (str/capitalize role)))]])

(defn people-row
  "The People row of the `people` who make the show or the episode, with a
  value for each one who has a name."
  [people]
  ["People" (map person (filter :name people))])

;; HTML, the aside element, and HTML-AAM: an aside in main is a
;; complementary landmark, labelled by its visible heading without the
;; name of its role (WAI-ARIA APG, landmark regions). HTML, the dl
;; element: a dt for each name, a dd for each value and a div around each
;; group. GOV.UK's summary list does the same.
;; https://w3c.github.io/html-aam/#el-aside-ancestorbodymain
;; https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/
;; https://html.spec.whatwg.org/multipage/grouping-content.html#the-dl-element
;; https://design-system.service.gov.uk/components/summary-list/
(defn aside
  "The Details of the `rows`, pairs of a name and its values, and the
  `people`, or nil when none of them has a value."
  [rows people]
  (when-let [rows (seq (keep (fn [[k vs]]
                               (when-let [vs (seq (remove nil? vs))]
                                 [k vs]))
                             (concat rows [(people-row people)])))]
    [:aside.details {:aria-labelledby id}
     ;; hidden, since the page shows what the aside is
     [:h2.visually-hidden {:id id} "Details"]
     [:dl
      (for [[k vs] rows]
        (into [:div {:replicant/key k} [:dt k]]
              (map #(vector :dd %) vs)))]]))

;; HTML, the article element: a self-contained composition that is
;; "independently distributable or reusable, e.g. in syndication", as a
;; show and an episode of a feed are
;; https://html.spec.whatwg.org/multipage/sections.html#the-article-element
;;
;; The Details and what follows them come after the content, even where
;; they show in the artwork's column, so that the order is the same at
;; every width (WCAG 2.2, SC 1.3.2 and 2.4.3). The content keeps its place
;; without them, e.g. while a show's feed is on its way, so that its
;; elements stay as they are when the feed arrives.
(defn article
  "The article of a show or an episode with the `attrs`: its `content`, a
  sequence of Hiccup that starts with its header, then its `aside`, if
  any, and `more`, e.g. the picks like the show."
  [attrs content aside more]
  (cond-> [:article.with-details attrs (into [:div] content) aside]
    more (conj more)))
