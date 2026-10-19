(ns dk.simongray.podcast-app.ui.details
  "The Details of a show or an episode, in the column of its page's
  artwork, or below its content on a phone: its metadata, e.g. the links
  that support the show, and the people who make it.

  The Show page and the Episode page each choose their rows, and this
  namespace writes them. Rows without a value are left out, and so are
  the Details when none of their rows has one."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.wary-fetch.url :as url]))

(def id
  "The id of the heading of the Details, which labels them."
  "details")

(defn host-text
  "The host of the web URL `s`, without its www., as a link shows it, or
  nil for a URL that isn't one of the web."
  [s]
  (when (url/web-url? s)
    (str/replace (url/host s) #"^www\." "")))

(defn web-link
  "A link to the web URL `s` that shows `text`, or else the host of the URL,
  which opens in a new tab, or nil for a URL that isn't one of the web."
  [s text]
  (when-let [host (host-text s)]
    (links/new-tab [:a {:href s} (or (some-> text str/trim not-empty) host)])))
;; podcast namespace, funding: the creator expects its text "to be
;; displayed in the app next to the link", which here is the link itself,
;; in the Details, among the show's other links
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/funding.md
(defn support
  "The links of the `funding`, with the text that the feed gives each, for
  a row of the Details."
  [funding]
  (keep #(some-> (web-link (:url %) (:content %))
                 (assoc-in [1 :replicant/key] (:url %)))
        funding))

(defn license
  "The `license`, as resolve/license gives it: the name and version of one
  of the namespace's list, or else its identifier, linked to its terms."
  [{:keys [content name version url] :as license}]
  (when-let [text (if name
                    (str/join " " (remove nil? [name version]))
                    (some-> content str/trim not-empty))]
    (or (web-link url text) text)))

;; podcast namespace, location: rel creator is where the content was made,
;; and subject, the default, what it's about. The node value is for display.
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/location.md
(defn location-rows
  "The rows of the `locations`, as resolve/locations gives them: Made in
  for where the content was made, and Location for what it's about, each
  name linked to a map where the feed says where it is."
  [locations]
  (let [label #(if (= "creator" (some-> (:rel %) str/lower-case)) "Made in" "Location")
        place #(when-let [place (some-> (:content %) str/trim not-empty)]
                 (or (web-link (resolve/location-url %) place) place))]
    (for [[k places] (group-by label locations)]
      [k (map place places)])))

;; W3C, Images tutorial, decorative images: the name beside a picture says
;; who it is, so its alt is empty
(defn person
  "A `person` who makes the show or the episode, as resolve/people gives
  them: a picture, the name, linked to their page where there's one, and
  the role."
  [{:keys [name role img href] :as person}]
  [:span.person
   (when (url/web-url? img)
     [:img {:src img :alt "" :width 48 :height 48 :loading "lazy" :decoding "async"}])
   [:span (or (web-link href name) name)
    (when role (str ", " (str/capitalize role)))]])

(defn people-row
  "The row of the `people` who make the show or the episode, People and a
  value for each of them with a name."
  [people]
  ["People" (map person (filter :name people))])

;; HTML, the aside element, and HTML-AAM: an aside in main is a
;; complementary landmark, whose visible heading labels it, without the
;; name of its role (WAI-ARIA APG, landmark regions). HTML, the dl element:
;; one dt for each name, a dd for each of its values, and a div around
;; each group. GOV.UK's summary list writes its facts the same way.
;; https://w3c.github.io/html-aam/#el-aside-ancestorbodymain
;; https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/
;; https://html.spec.whatwg.org/multipage/grouping-content.html#the-dl-element
;; https://design-system.service.gov.uk/components/summary-list/
(defn aside
  "The Details of the `rows`, pairs of a name and its values, and the
  `people`, or nil when none of the rows has a value and there are no
  people."
  [rows people]
  (when-let [rows (seq (keep (fn [[k vs]]
                               (when-let [vs (seq (remove nil? vs))]
                                 [k vs]))
                             (concat rows [(people-row people)])))]
    [:aside.details {:aria-labelledby id}
     ;; only for a screen reader, since the page shows what the aside is
     [:h2.visually-hidden {:id id} "Details"]
     [:dl
      (for [[k vs] rows]
        (into [:div {:replicant/key k} [:dt k]]
              (map #(vector :dd %) vs)))]]))

;; HTML, the article element: "a complete, or self-contained, composition
;; in a document, page, application, or site and that is, in principle,
;; independently distributable or reusable, e.g. in syndication", as a show
;; and an episode of a feed are. Its Details are an aside within it.
;; https://html.spec.whatwg.org/multipage/sections.html#the-article-element
;;
;; The Details and what follows them come after the content, in the column
;; of the artwork where there's room for it, and below the content where
;; there isn't, so that the order is the same at every width (WCAG 2.2, SC
;; 1.3.2 and 2.4.3). The content keeps its place without them, e.g. while
;; the feed of a show is on its way, so that its elements stay as they are
;; when they arrive.
(defn article
  "The article of a show or an episode with the `attrs`: its `content`, a
  sequence of Hiccup that begins with its header, with its `aside`, when
  there's one, and `more` after the aside, e.g. the picks like the show."
  [attrs content aside more]
  (cond-> [:article.with-details attrs (into [:div] content) aside]
    more (conj more)))
