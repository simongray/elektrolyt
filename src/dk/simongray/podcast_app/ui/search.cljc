(ns dk.simongray.podcast-app.ui.search
  "The views of the Search page: the form, the status line of the search
  and its results, which the listener can subscribe to.

  The state holds each search under its query in :searches, as
  dk.simongray.podcast-app.search describes it."
  (:require [clojure.string :as str]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.subscription :as subscription]))

(defn query
  "The query of the Search page of the `state`, or nil."
  [state]
  (let [q (get-in state [:route :query-params :q])]
    (when-not (str/blank? q)
      q)))

;; MDN, <search>: the element is the search landmark, and the results go
;; outside it, in the main content. The query goes in the URL, so that Back,
;; a reload and a shared link show the same search.
(defn search-form
  "The form that searches for words or a link, which shows the query `q`."
  [q]
  [:search
   [:form {:action (routes/href :route/search)
           :on     {:submit [[:event/prevent-default]
                             [:search/submit [:form/field "q"]]]}}
    [:label {:for "search-query"} "Search for podcasts, or paste a link"]
    [:input#search-query {:type "search" :name "q" :value (or q "")}]
    [:button {:type "submit"} "Search"]]])

(defn listing
  "The strings `xs` as an English list, e.g. \"a, b and c\"."
  [xs]
  (if (next xs)
    (str (str/join ", " (butlast xs)) " and " (last xs))
    (first xs)))

(defn- found-text
  "What the search for `q` found, as the status line starts to tell it: `n`
  results, with the ids of the directories that `failed`."
  [q n failed]
  (let [link? (search/link? q)]
    (cond
      (and link? (pos? n)) (str n (if (= 1 n) " podcast feed" " podcast feeds"))
      (pos? n)             (str n (if (= 1 n) " show" " shows"))
      link?                "No podcast feeds at that link"
      ;; no match is certain only when every directory answered
      (seq failed)         "No shows found"
      :else                (str "No shows match “" q "”"))))

(defn status-text
  "What the status line says of the `search` for the query `q`, or nil
  before there's a search."
  [q {:keys [status results failed error] :as search}]
  (cond
    (nil? search)         nil
    (= :searching status) "Searching…"
    error                 "The search failed. Try again."
    :else                 (str (found-text q (count results) failed)
                               "."
                               (when (seq failed)
                                 (str " " (listing (map search/source-name failed))
                                      " didn't answer.")))))

;; WCAG 2.2, SC 4.1.3 Status Messages: the status role lets a screen reader
;; tell how the search went without moving the focus. It says "Searching…"
;; until every directory has answered, so that the results that arrive in
;; between aren't read out each time.
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
(defn result
  "A show that a search found, the `result`, in the `state` with the
  `subscribed` URLs. When `shared-title?`, it shows the URL of its feed."
  [state subscribed {:keys [url title author] :as result} shared-title?]
  (let [title (or title url)]
    [:article
     (artwork/img result 64 {:loading "lazy"})
     [:h2 (show-link (assoc result :title title))]
     (when author
       [:p author])
     (when shared-title?
       [:p url])
     (subscription/button (subscription/status state subscribed url)
                          url
                          (cond-> title shared-title? (str ", " url)))
     (subscription/error state url)]))

(defn results
  "The list of the `search`'s results in the `state`."
  [state {:keys [results] :as search}]
  (let [subscribed (search/subscribed-urls (:library state))
        shared     (->> (frequencies (map :title results))
                        (keep (fn [[title n]] (when (< 1 n) title)))
                        (set))]
    (when (seq results)
      [:ol.search-results {:role "list"}
       (for [{:keys [url title] :as r} results]
         [:li {:replicant/key url}
          (result state subscribed r (contains? shared title))])])))

(defn content
  "The content of the Search page of the `state`, below its heading."
  [state]
  (let [q      (query state)
        search (get-in state [:searches q])]
    (list (search-form q)
          (status-line q search)
          (results state search))))
