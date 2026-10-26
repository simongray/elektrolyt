(ns dk.simongray.elektrolyt.ui.routes
  "The pages of the app by name and path, the paths of links to them, and
  the actions of a navigation."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.elektrolyt.search :as search]
            [reitit.core :as reitit]))

;; A show's page is at /shows/<guid>/<slug>, as a question is on Stack
;; Overflow: the guid finds the show, and the slug of its title is only for
;; whoever reads the link, so any slug or none will do. A titled route
;; names its page as :page.
;;
;; An episode's page is under its show's, since an item's guid is unique
;; only within its feed, as podcast:remoteItem has it. The item goes by a
;; short hash of its guid, as git names a commit, since the guid is often a
;; URL, whose slashes many servers refuse in a path even percent-encoded.
;; The slug tells apart two items with the same hash.
;; https://podcasting2.org/docs/podcast-namespace/tags/remote-item
(def router
  "The router of the app's paths, whose routes are named :route/…"
  (reitit/router
   [["/"                                 :route/home]
    ["/queue"                            :route/queue]
    ["/inbox"                            :route/inbox]
    ["/library"                          :route/library]
    ["/search"                           :route/search]
    ["/settings"                         :route/settings]
    ["/shows/:guid"                      :route/show]
    ["/shows/:guid/:slug"                {:name :route/titled-show :page :route/show}]
    ["/shows/:guid/episodes/:hash"       :route/episode]
    ["/shows/:guid/episodes/:hash/:slug" {:name :route/titled-episode :page :route/episode}]]))

(defn decodes?
  "Whether the run of percent `escapes` decodes as UTF-8."
  [escapes]
  #?(:clj  (let [b (byte-array (map #(unchecked-byte (Integer/parseInt (subs % 1) 16))
                                    (re-seq #"%.." escapes)))]
             ;; malformed bytes decode as U+FFFD, which encodes otherwise
             (= (seq b) (seq (.getBytes (String. b "UTF-8") "UTF-8"))))
     :cljs (try (js/decodeURIComponent escapes) true
                (catch :default _ false))))

(defn decodable
  "The URL `url` with each percent sign escaped itself where it doesn't
  start an escape, e.g. \"/search?q=100%25\" for \"/search?q=100%\", or
  starts escapes that aren't UTF-8, e.g. \"%25FF\" for \"%FF\", since the
  router can't decode either."
  [url]
  (-> url
      (str/replace #"%(?![0-9A-Fa-f]{2})" "%25")
      (str/replace #"(?:%[0-9A-Fa-f]{2})+"
                   #(if (decodes? %) % (str/replace % "%" "%25")))))

(defn href
  "The path of the route `route-name` with its `path-params`,
  `query-params` and `fragment`, e.g. \"/settings#search\"."
  ([route-name]
   (href route-name nil nil))
  ([route-name path-params]
   (href route-name path-params nil))
  ([route-name path-params query-params]
   (href route-name path-params query-params nil))
  ([route-name path-params query-params fragment]
   (cond-> (-> (reitit/match-by-name router route-name path-params)
               (reitit/match->path query-params))
     fragment (str "#" fragment))))

(defn- decomposed
  "The string `s` with each accented letter as its letter and its accents,
  in Unicode's normalization form NFKD."
  [s]
  #?(:clj  (java.text.Normalizer/normalize s java.text.Normalizer$Form/NFKD)
     :cljs (.normalize s "NFKD")))

(def foldings
  "How a slug spells the letters that have no accent to take off, e.g. o
  for ø."
  {"ø" "o" "æ" "ae" "œ" "oe" "ß" "ss" "đ" "d" "ł" "l" "þ" "th" "ı" "i"})

(defn slug
  "The words of `title` as lower-case letters and digits between hyphens,
  at most 60 characters of them, or nil when it has none.

      (slug \"Podcasting 2.0\")  ; => \"podcasting-2-0\"
      (slug \"Mørkeland\")       ; => \"morkeland\""
  [title]
  ;; decomposed first, since NFKD turns letters such as 𝐓 and ℌ into capitals
  (let [words (-> (decomposed (str title))
                  (str/lower-case)
                  ;; the combining accents that NFKD splits off the letters
                  (str/replace #"[̀-ͯ]" "")
                  (str/replace #"[øæœßđłþı]" foldings)
                  (str/replace #"[^a-z0-9]+" "-"))]
    (-> (subs words 0 (min 60 (count words)))
        (str/replace #"^-+|-+$" "")
        (not-empty))))

(defn show-route
  "The route name, path params and query params, in that order, of the
  page of the show `guid` titled `title`, with its feed's `url` for a show
  outside the library."
  [guid title url]
  (let [query (when url {:url url})]
    (if-let [s (slug title)]
      [:route/titled-show {:guid guid :slug s} query]
      [:route/show {:guid guid} query])))

(defn show-href
  "The path of the page of the show `guid` titled `title`, with its feed's
  `url` for a show outside the library."
  ([guid title]
   (show-href guid title nil))
  ([guid title url]
   (apply href (show-route guid title url))))

;; Pocket Casts and Spotify share an episode at a time with ?t=, and a link
;; with a query is a page of its own, which a fragment isn't
;; (Media Fragments URI 1.0, section 3.2)
(defn episode-href
  "The path of the page of the episode `item-guid` titled `title` in the
  show `guid`, with the feed's `url` for a show outside the library, and
  the time `t` in seconds, if given."
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
  "The reitit `match` as a route: the :name of its page, its :path-params
  and :query-params, and its :slug and :fragment where it has them, or nil
  for no match."
  [{:keys [data path-params query-params fragment] :as match}]
  (when match
    (cond-> {:name         (or (:page data) (:name data))
             :path-params  (dissoc path-params :slug)
             :query-params (or query-params {})}
      (:slug path-params) (assoc :slug (:slug path-params))
      (seq fragment)      (assoc :fragment fragment))))

(defn route-at
  "The route of the page at `path`, without a query or a fragment, or nil
  for no page, e.g. before the router has started."
  [path]
  (match->route (reitit/match-by-path router path)))

(defn same-page?
  "Whether the routes `a` and `b` are the same page, whatever their slugs,
  query parameters and fragments."
  [a b]
  (= (dissoc a :query-params :slug :fragment)
     (dissoc b :query-params :slug :fragment)))

(defn navigated
  "The actions of the navigation to `route` from `state`: keep the route,
  and prepare the page, e.g. run its search, load its show or move the
  focus to its heading. A new page that had the `scroll` position when it
  was left, e.g. one gone back to, scrolls back there instead.

  The first page, and a page that only changes its query, keep the focus
  at the start of the document unless the URL's fragment names a part of
  it."
  ([state route]
   (navigated state route nil))
  ([state route scroll]
   (let [{:keys [q url]} (:query-params route)
         new-page?       (and (contains? state :route)
                              (not (same-page? route (:route state))))
         restore?        (and new-page? (some? scroll))]
     (cond-> [[:store/assoc :route route]]
       ;; forget the reads of episodes' files only on a new page, since an
       ;; Episode page reads its own again as it mounts
       new-page?
       (conj (if restore? [:page/restore-scroll scroll] [:page/focus-heading])
             [:episodes/forget])

       (and (= :route/search (:name route))
            (not (str/blank? q))
            (or (not (get-in state [:searches q]))
                (search/outdated? state q)))
       (conj [:search/run q])

       (#{:route/show :route/episode} (:name route))
       (conj [:show/load (get-in route [:path-params :guid]) url false])

       (= :route/settings (:name route))
       (conj [:storage/check])

       ;; last, so that it moves the focus on from the heading
       (and (:fragment route) (not restore?))
       (conj [:page/scroll-to (:fragment route)])))))

(comment
  (href :route/library)
  ;; => "/library"
  (match->route (reitit/match-by-path router "/inbox"))
  ;; => {:name :route/inbox, :path-params {}, :query-params {}}
  (show-href "917393e3-1b1e-5cef-ace4-edaa54e1f810" "Podcasting 2.0")
  ;; => "/shows/917393e3-1b1e-5cef-ace4-edaa54e1f810/podcasting-2-0"
  #_.)
