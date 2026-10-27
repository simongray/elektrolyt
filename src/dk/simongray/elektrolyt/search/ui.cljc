(ns dk.simongray.elektrolyt.search.ui
  "The views of the Search page: the form, the status line of the search,
  and its results, which the listener can subscribe to."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.library.settings.ui :as settings-ui]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.loading :as loading]
            [dk.simongray.elektrolyt.ui.subscription :as subscription]
            [dk.simongray.elektrolyt.ui.text :as text]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn query
  "The query of the Search page in `state`, or nil."
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
;; The listener turns drpodcast.nu on and off with DR's episodes in the DR
;; group of Settings, so it has a line of its own
(defn search-form
  "The form that searches for words or a link, with the query `q` and the
  directories `sources` that it asks."
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
  "The text of what a search of a link found, its `n` feeds."
  [n]
  (if (pos? n)
    (str (text/counted n "podcast feed") ".")
    "No podcast feeds at that link."))

(defn directories-text
  "The text of what the `search` for the words `q` found in each
  directory, e.g. \"Found 20 shows in Apple Podcasts and 3 in fyyd.
  gpodder.net didn't answer.\""
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
  "The status line's text for the `search` of the query `q`, or nil before
  a search."
  [q {:keys [results lists error] :as search}]
  (cond
    (nil? search)               nil
    (search/searching? search)  "Searching…"
    error                       "The search failed. Try again."
    lists                       (directories-text q search)
    :else                       (link-text (count results))))

;; WCAG 2.2, SC 4.1.3 Status Messages: the status role lets a screen reader
;; tell how the search went without moving the focus. The line reads
;; "Searching…" until every directory has answered, and then sums them up
;; in one message, since several that arrive at once are read as badly as
;; none (Scott O'Hara, Are we live?).
;; https://www.scottohara.me/blog/2022/02/05/are-we-live.html
(defn status-line
  "The status line of the `search` for `q`, on the page even before the
  search starts."
  [q search]
  (if (some-> search search/searching?)
    ;; a line of its own still, so that the lists don't move once it sums
    ;; them up
    [:p.searching {:role "status"} [:span.visually-hidden (status-text q search)]]
    [:p {:role "status"} (status-text q search)]))

(defn show-link
  "The link to the page of the show in the search `result`, which fetches
  the start of its feed when a pointer rests on it."
  [{:keys [url guid title] :as result}]
  [:a {:href (routes/show-href guid title url)
       :on   (show/prefetching guid url)}
   title])

;; A site often has several feeds of one show, e.g. in MP3 and in Opus, so a
;; title that another result shares comes with the URL of its feed (WCAG
;; 2.2, SC 2.4.6 Headings and Labels)
;;
;; TODO: mark a show that other directories have too, e.g. with a small
;; icon for each, as SearXNG names the engines that found a result. A show
;; in several directories is most likely the real feed, rather than a
;; re-upload or an abandoned feed of the same title.
;; https://github.com/searxng/searxng/blob/master/searx/templates/simple/macros.html
(defn result
  "The search `result` in `state` with the `subscribed` URLs, under a
  heading of `level`, e.g. :h3, with its artwork and title linking to its
  page, after the mark of a show that's subscribed, and its feed URL when
  `shared-title?`."
  [state subscribed level {:keys [url guid title author] :as result} shared-title?]
  (let [title (or title url)]
    [:article
     (artwork/linked (routes/show-href guid title url) title
                     (artwork/img result 64 (merge {:loading "lazy"} (colors/placeholder state result)))
                     {:on (show/prefetching guid url)})
     [level (cond->> (show-link (assoc result :title title))
              (= :subscribed (subscription/status state subscribed url guid))
              (subscription/marked))]
     (when author
       [:p author])
     (when shared-title?
       [:p url])]))

(defn results
  "The list of `results` in `state`, each under a heading of `level`."
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
  "The id of the heading over the list of the directory `source`, which
  the jump row moves the focus to."
  [source]
  (str "search-" (name source)))

(defn section-id
  "The id of the list of the directory `source`, which the jump row brings
  into view."
  [source]
  (str "search-list-" (name source)))

(defn list-text
  "The text over the directory list `l` for the words `q`, e.g. the date
  of the cached list of a directory that didn't answer."
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
;;
;; The button to try again goes as the directory is asked again, so the
;; focus moves to the list's heading
(defn directory-list
  "The list `l` of the directory `source` for the words `q` in `state`: its
  heading, its results, and a button to try again if it didn't answer."
  [state q source {:keys [status] :as l}]
  [:section.search-list {:replicant/key source :id (section-id source)}
   [:h2 {:id (list-id source) :tabindex -1} (search/source-name source)]
   (if (= :failed status)
     (notices/line {}
                   (list-text q l)
                   [controls/button {:icon         icons/arrows-clockwise
                                     :data-refocus (str "#" (list-id source))
                                     :on           {:click [[:search/retry q source]]}}
                    "Try again" [:span.visually-hidden " " (search/source-name source)]])
     [(if (= :searching status) :p.visually-hidden :p) (list-text q l)])
   (if (and (= :searching status) (empty? (:results l)))
     (loading/rows 3)
     (results state :h3 (:results l)))])

(defn jump-label
  "What a link in the jump row shows of the list `l` after its directory's
  name: the count, or a mark while searching or after failing, each with
  words for a screen reader."
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
  "The links to the `lists` of the `sources` of the search for `q`, each
  with what it found, marking the list `shown` last as current."
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
  "The directories' lists for the `search` of the words `q` in `state`, in
  the listener's order, side by side on a wide screen and stacked on a
  narrow one."
  [state q {:keys [sources lists shown] :as search}]
  (list
   (when (next sources)
     (jump-row q sources lists shown))
   [:div.search-lists
    (for [source sources]
      (directory-list state q source (get lists source)))]))

(defn content
  "The content of the Search page in `state`, below its heading."
  [state]
  (let [q      (query state)
        search (get-in state [:searches q])]
    (list (search-form q (search/sources state))
          (status-line q search)
          (if (:lists search)
            (directory-lists state q search)
            (results state :h2 (:results search))))))
