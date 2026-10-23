(ns dk.simongray.elektrolyt.ui.search
  "The views of the Search page: the form, the status line of the search
  and its results, which the listener can subscribe to.

  The state holds each search under its query in :searches, as
  dk.simongray.elektrolyt.search describes it."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.colors :as colors]
            [dk.simongray.elektrolyt.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.text :as text]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.settings :as settings-ui]
            [dk.simongray.elektrolyt.ui.subscription :as subscription]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn query
  "The query of the Search page of the `state`, or nil."
  [state]
  (let [q (get-in state [:route :query-params :q])]
    (when-not (str/blank? q)
      q)))

;; MDN, <search>: the element is the search landmark, and the results go
;; outside it, in the main content. The query goes in the URL, so that Back,
;; a reload and a shared link show the same search.
;;
;; NN/g, scoped search: say where a search looks, near the box, with a way
;; to change it, which is the Search group of the Settings page
;; https://www.nngroup.com/articles/scoped-search/
;;
;; drpodcast.nu isn't among the directories there, since the DR group turns
;; it on and off with DR's episodes, so it has a line of its own
(defn search-form
  "The form that searches for words or a link, which shows the query `q`,
  and the directories `sources` that it asks."
  [q sources]
  (let [dr? (some #{drpodcast/source} sources)]
    [:search
     [:form {:action (routes/href :route/search)
             :on     {:submit [[:event/prevent-default]
                               [:search/submit [:form/field "q"]]]}}
      [:label {:for "search-query"} "Search for podcasts, or paste a link"]
      [:input#search-query {:type "search" :name "q" :value (or q "")}]
      [:button.primary {:type "submit"} "Search"]]
     [:p "Searches " (text/listing (map search/source-name (remove #{drpodcast/source} sources))) ". "
      [:a {:href (settings-ui/group-href :search)}
       "Change" [:span.visually-hidden " the directories"]]]
     (when dr?
       [:p "Also searches drpodcast.nu for DR's programmes. "
        [:a {:href (settings-ui/group-href :dr)}
         "Change" [:span.visually-hidden " the search of drpodcast.nu"]]])]))

(defn link-text
  "What the search of the link `q` found: its `n` feeds."
  [n]
  (if (pos? n)
    (str (text/counted n "podcast feed") ".")
    "No podcast feeds at that link."))

(defn directories-text
  "What the search for the words `q` found in its directories, the
  `sources` with their `lists`, e.g. \"Found 20 shows in Apple Podcasts
  and 3 in fyyd. gpodder.net didn't answer.\""
  [q {:keys [sources lists] :as search}]
  (let [of     (fn [status] (filter #(= status (get-in lists [% :status])) sources))
        done   (of :done)
        failed (of :failed)
        n      #(count (get-in lists [% :results]))
        found  (map-indexed (fn [i s]
                              (str (if (zero? i) (text/counted (n s) "show") (n s))
                                   " in " (search/source-name s)))
                            done)]
    (str/trim
     (str (cond
            (pos? (reduce + (map n done))) (str "Found " (text/listing found) ".")
            (seq done)                     (str "No shows match “" q "”."))
          (when (seq failed)
            (str " " (text/listing (map search/source-name failed)) " didn't answer."))))))

(defn status-text
  "What the status line says of the `search` for the query `q`, or nil
  before there's a search."
  [q {:keys [results lists error] :as search}]
  (cond
    (nil? search)               nil
    (search/searching? search)  "Searching…"
    error                       "The search failed. Try again."
    lists                       (directories-text q search)
    :else                       (link-text (count results))))

;; WCAG 2.2, SC 4.1.3 Status Messages: the status role lets a screen reader
;; tell how the search went without moving the focus. It says "Searching…"
;; until every directory has answered, and then sums them all up in one
;; message, since several that arrive at once are read as badly as none
;; (Scott O'Hara, Are we live?).
;; https://www.scottohara.me/blog/2022/02/05/are-we-live.html
(defn status-line
  "The status line of the `search` for `q`, which is on the page before the
  search starts."
  [q search]
  [:p {:role "status"} (status-text q search)])

;; instant.page: a pointer that rests on a link for 65 ms clicks it about
;; half the time, and leaves some 300 ms to fetch what the page needs. Only
;; the start of the feed is fetched, which parses in a moment.
(defn show-link
  "The link to the page of the show that a search found, the `result`,
  which starts to fetch the start of its feed when a pointer rests on it."
  [{:keys [url guid title] :as result}]
  [:a {:href (routes/show-href guid title url)
       :on   {:mouseenter  [[:show/prefetch-soon guid url]]
              :mouseleave  [[:show/prefetch-cancel]]
              :pointerdown [[:show/load guid url true]]}}
   title])

;; A site often has several feeds of one show, e.g. in MP3 and in Opus, so a
;; title that another result shares comes with the URL of its feed, which
;; the name of the button has too (WCAG 2.2, SC 2.4.6 Headings and Labels)
;;
;; TODO: mark a show that other directories also have, e.g. with a small,
;; discreet icon for each of them, as SearXNG names the engines that found
;; a result. A show that several directories carry is most likely its real
;; feed, rather than a re-upload or an abandoned feed of the same title.
;; https://github.com/searxng/searxng/blob/master/searx/templates/simple/macros.html
(defn result
  "A show that a search found, the `result`, in the `state` with the
  `subscribed` URLs, under a heading of the `level`, e.g. :h3, its artwork
  and title a link to its page. When `shared-title?`, it shows the URL of
  its feed."
  [state subscribed level {:keys [url guid title author] :as result} shared-title?]
  (let [title (or title url)]
    [:article
     (artwork/linked (routes/show-href guid title url) title
                     (artwork/img result 64 (merge {:loading "lazy"} (colors/placeholder state result))))
     [level (show-link (assoc result :title title))]
     (when author
       [:p author])
     (when shared-title?
       [:p url])
     (subscription/button (subscription/status state subscribed url)
                          url
                          (cond-> title shared-title? (str ", " url)))
     (subscription/error state url)]))

(defn results
  "The list of the `results` in the `state`, each under a heading of the
  `level`."
  [state level results]
  (let [subscribed (search/subscribed-urls (:library state))
        shared     (->> (frequencies (map :title results))
                        (keep (fn [[title n]] (when (< 1 n) title)))
                        (set))]
    (when (seq results)
      [:ol.search-results {:role "list"}
       (for [{:keys [url title] :as r} results]
         [:li {:replicant/key url}
          (result state subscribed level r (contains? shared title))])])))

(defn list-id
  "The id of the heading of the list of the directory `source`, which the
  jump row moves the focus to."
  [source]
  (str "search-" (name source)))

(defn section-id
  "The id of the list of the directory `source`, which the jump row brings
  into view."
  [source]
  (str "search-list-" (name source)))

(defn list-text
  "What the list of a directory says of itself, `l`, for the words `q`,
  e.g. the date of the list that the cache kept of a directory that didn't
  answer."
  [q {:keys [status results kept-at] :as l}]
  (case status
    :searching "Searching…"
    :failed    (if kept-at
                 (str "Didn't answer, so these are its shows of "
                      (time-ui/date-text kept-at) ".")
                 "Didn't answer.")
    (if (seq results)
      (text/counted (count results) "show")
      (str "No shows match “" q "”."))))

;; Duke's bento search: one box of results for each source, since a
;; ranking across very different sources is hard to make, and a library's
;; users weren't confused by the boxes (NCSU)
;; https://blogs.library.duke.edu/bitstreams/2014/07/03/bento-is-coming/
;; https://www.lib.ncsu.edu/node/45126
(defn directory-list
  "The list of the directory `source`, `l`, for the words `q`, in the
  `state`: its heading, what it found, and a way to ask it again where it
  didn't answer."
  [state q source {:keys [status] :as l}]
  [:section.search-list {:replicant/key source :id (section-id source)}
   [:h2 {:id (list-id source) :tabindex -1} (search/source-name source)]
   [:p (list-text q l)]
   (when (= :failed status)
     [:button {:type "button" :on {:click [[:search/retry q source]]}}
      "Try again" [:span.visually-hidden " " (search/source-name source)]])
   (results state :h3 (:results l))])

(defn jump-label
  "What a link of the jump row shows of the list `l` after the name of its
  directory: its count, or a mark while it searches or where it failed,
  each with words for a screen reader."
  [{:keys [status results] :as l}]
  (case status
    :searching (list [:span {:aria-hidden "true"} " …"]
                     [:span.visually-hidden ", searching"])
    :failed    (list [:span {:aria-hidden "true"} " –"]
                     [:span.visually-hidden ", didn't answer"])
    (list " " (str (count results))
          [:span.visually-hidden " " (text/plural (count results) "show")])))

;; GOV.UK's tabs, and Inclusive Components': a contents of same-page links
;; above sections that all stay on the page, rather than a widget that
;; hides them. A link brings the whole of its list into view, sideways in
;; a row of them and down in a stack, and moves the focus to its heading,
;; as the skip link does. The link of the list last shown is the current
;; one (WAI-ARIA 1.2, aria-current).
;; https://design-system.service.gov.uk/components/tabs/
;; https://inclusive-components.design/tabbed-interfaces/
(defn jump-row
  "The links to the lists of the `sources` of the search for `q`, `lists`,
  with what each found, where the one `shown` last is the current one."
  [q sources lists shown]
  [:nav.search-jump {:aria-label "Directories"}
   [:ul {:role "list"}
    (for [source sources]
      [:li {:replicant/key source}
       [:a {:href         (str "#" (section-id source))
            :aria-current (when (= shown source) "true")
            :on           {:click [[:event/prevent-default]
                                   [:store/assoc-in [:searches q :shown] source]
                                   [:page/show (list-id source)]]}}
        (search/source-name source) (jump-label (get lists source))]])]])

(defn directory-lists
  "The lists of the directories of the `search` for the words `q` in the
  `state`, in the listener's order, side by side on a wide screen and one
  below the other on a narrow one."
  [state q {:keys [sources lists shown] :as search}]
  (list
   (when (next sources)
     (jump-row q sources lists shown))
   [:div.search-lists
    (for [source sources]
      (directory-list state q source (get lists source)))]))

(defn content
  "The content of the Search page of the `state`, below its heading."
  [state]
  (let [q      (query state)
        search (get-in state [:searches q])]
    (list (search-form q (search/sources state))
          (status-line q search)
          (if (:lists search)
            (directory-lists state q search)
            (results state :h2 (:results search))))))
