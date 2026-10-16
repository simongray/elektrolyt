(ns dk.simongray.podcast-app.routes
  "The pages of the app by name and path, and the paths of the links to them."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.guid :as guid]
            [reitit.core :as reitit]))

;; A show's page is at /shows/<guid>/<slug>, as a question is on Stack
;; Overflow: the guid names the show, and the slug of its title is there for
;; a reader of the link alone, so the guid finds the show with any slug or
;; none. The route keeps the page as its :page.
;;
;; An episode's page is under its show's, since an item's guid names it only
;; within its feed, as podcast:remoteItem names it by both. The item is
;; named by the short hash of its guid, as git names a commit, since the
;; guid is often a URL, whose slashes many servers refuse in a path even
;; percent-encoded, and the slug tells apart two items of one hash.
;; https://podcasting2.org/docs/podcast-namespace/tags/remote-item
(def router
  "The router of the app's paths, whose routes are named :route/…"
  (reitit/router
   [["/"                                 :route/queue]
    ["/inbox"                            :route/inbox]
    ["/library"                          :route/library]
    ["/search"                           :route/search]
    ["/settings"                         :route/settings]
    ["/shows/:guid"                      :route/show]
    ["/shows/:guid/:slug"                {:name :route/titled-show :page :route/show}]
    ["/shows/:guid/episodes/:hash"       :route/episode]
    ["/shows/:guid/episodes/:hash/:slug" {:name :route/titled-episode :page :route/episode}]]))

(defn href
  "The path of the route named `route-name`, with its `path-params` and
  `query-params`."
  ([route-name]
   (href route-name nil nil))
  ([route-name path-params]
   (href route-name path-params nil))
  ([route-name path-params query-params]
   (-> (reitit/match-by-name router route-name path-params)
       (reitit/match->path query-params))))

(defn- decomposed
  "The string `s` with each accented letter as its letter and its accents,
  in Unicode's normalization form NFKD."
  [s]
  #?(:clj  (java.text.Normalizer/normalize s java.text.Normalizer$Form/NFKD)
     :cljs (.normalize s "NFKD")))

(def foldings
  "The letters that have no accents to take off, as the letters of a slug."
  {"ø" "o" "æ" "ae" "œ" "oe" "ß" "ss" "đ" "d" "ł" "l" "þ" "th"})

(defn slug
  "The words of `title` as lower-case letters and digits between hyphens,
  at most 60 characters of them, or nil when it has none.

      (slug \"Podcasting 2.0\")  ; => \"podcasting-2-0\"
      (slug \"Mørkeland\")       ; => \"morkeland\""
  [title]
  (let [words (-> (str/lower-case (str title))
                  (decomposed)
                  ;; the combining accents that NFKD takes off the letters
                  (str/replace #"[̀-ͯ]" "")
                  (str/replace #"[øæœßđłþ]" foldings)
                  (str/replace #"[^a-z0-9]+" "-"))]
    (-> (subs words 0 (min 60 (count words)))
        (str/replace #"^-+|-+$" "")
        (not-empty))))

(defn show-href
  "The path of the page of the show `guid` titled `title`, with the `url` of
  its feed for a show that the library doesn't keep."
  ([guid title]
   (show-href guid title nil))
  ([guid title url]
   (let [query (when url {:url url})]
     (if-let [s (slug title)]
       (href :route/titled-show {:guid guid :slug s} query)
       (href :route/show {:guid guid} query)))))

;; Pocket Casts and Spotify share an episode at a time with ?t=, and a link
;; with a query is a page of its own, which a fragment isn't
;; (Media Fragments URI 1.0, section 3.2)
(defn episode-href
  "The path of the page of the episode `item-guid` titled `title` of the
  show `guid`, with the `url` of its feed for a show that the library
  doesn't keep, and at `t` seconds into the episode when it's given."
  ([guid item-guid title url]
   (episode-href guid item-guid title url nil))
  ([guid item-guid title url t]
   (let [path  {:guid guid :hash (guid/short-hash item-guid)}
         query (not-empty (cond-> {}
                            url (assoc :url url)
                            t   (assoc :t (long t))))]
     (if-let [s (slug title)]
       (href :route/titled-episode (assoc path :slug s) query)
       (href :route/episode path query)))))

(defn match->route
  "The route of the reitit `match` as it's kept in the state: the :name of
  its page, its :path-params but the slug, which is its :slug, and its
  :query-params, or nil for a path of no route."
  [{:keys [data path-params query-params] :as match}]
  (when match
    (cond-> {:name         (or (:page data) (:name data))
             :path-params  (dissoc path-params :slug)
             :query-params (or query-params {})}
      (:slug path-params) (assoc :slug (:slug path-params)))))

(defn same-page?
  "Whether the routes `a` and `b` are the same page, perhaps with another
  slug or other query parameters, e.g. the Search page of another query."
  [a b]
  (= (dissoc a :query-params :slug) (dissoc b :query-params :slug)))

(comment
  (href :route/library)
  ;; => "/library"
  (match->route (reitit/match-by-path router "/inbox"))
  ;; => {:name :route/inbox, :path-params {}, :query-params {}}
  (show-href "917393e3-1b1e-5cef-ace4-edaa54e1f810" "Podcasting 2.0")
  ;; => "/shows/917393e3-1b1e-5cef-ace4-edaa54e1f810/podcasting-2-0"
  #_.)
