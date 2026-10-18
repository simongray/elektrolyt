(ns dk.simongray.podcast-app.ui.details
  "The metadata of a show or an episode as its page shows it: a line of
  facts under the title, the links that support the show, and the Details
  beside the content, with the people who make it.

  The Show page and the Episode page each choose their facts and rows, and
  this namespace writes them. Facts and rows without a value are left out,
  and so are the Details when none of their rows has one."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.wary-fetch.url :as url]))

(def id
  "The id of the heading of the Details, which the link to them moves the
  focus to."
  "details")

;; HTML, section 4.5.14 as in the list of episodes: the dot between two
;; facts is decoration, which a screen reader skips
(defn facts-line
  "The `facts` in a line, e.g. Technology · Explicit, or nil when none of
  them has a value."
  [facts]
  (when-let [facts (seq (remove nil? facts))]
    (into [:p] (interpose [:span {:aria-hidden "true"} " · "] facts))))

;; As the jump row of the Search page: a link to a part of the same page,
;; which brings its heading into view and moves the focus to it. On a
;; phone, the Details come after the whole list of episodes. Where they're
;; beside the content, the CSS hides the link (see app.css).
(def link
  "The link to the Details, which only the arrow tells apart from the name
  of their heading."
  [:p.details-link
   [:a {:href (str "#" id)
        :on   {:click [[:event/prevent-default] [:page/show id]]}}
    "Details" [:span {:aria-hidden "true"} " ↓"]]])

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
;; displayed in the app next to the link"
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/funding.md
(defn support
  "The links of the `funding`, with the text that the feed gives each, or
  nil without one."
  [funding]
  (when-let [links (seq (keep #(some-> (web-link (:url %) (:content %))
                                       (assoc-in [1 :replicant/key] (:url %)))
                              funding))]
    (into [:p "Support the show: "] (interpose ", " links))))

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
(defn people
  "The `people` who make the show or the episode, as resolve/people gives
  them: a picture, the name, linked to their page where there's one, and
  the role, or nil without them."
  [people]
  (when-let [people (seq (filter :name people))]
    (list
     [:h3 "People"]
     [:ul.people {:role "list"}
      (for [{:keys [name role img href]} people]
        [:li {:replicant/key (str name "/" role)}
         (when (url/web-url? img)
           [:img {:src img :alt "" :width 48 :height 48 :loading "lazy" :decoding "async"}])
         [:span (or (web-link href name) name)
          (when role (str ", " (str/capitalize role)))]])])))

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
  [rows people-list]
  (let [rows (keep (fn [[k vs]]
                     (when-let [vs (seq (remove nil? vs))]
                       [k vs]))
                   rows)
        made (people people-list)]
    (when (or (seq rows) made)
      [:aside.details {:aria-labelledby id}
       [:h2 {:id id :tabindex -1} "Details"]
       (when (seq rows)
         [:dl
          (for [[k vs] rows]
            (into [:div {:replicant/key k} [:dt k]]
                  (map #(vector :dd %) vs)))])
       made])))

;; Every Layout, Sidebar: the Details beside the content where there's
;; room for both, and below it where there isn't. They come last, so that
;; the order is the same at every width (WCAG 2.2, SC 1.3.2 and 2.4.3).
;; The content keeps its place without them, e.g. while the feed of a show
;; is on its way, so that its elements stay as they are when they arrive.
;; https://every-layout.dev/layouts/sidebar/
(defn with-aside
  "The `content` of a page, a sequence of Hiccup, with its `aside` beside
  it, when there's one."
  [content aside]
  [:div.with-details (into [:div] content) aside])
