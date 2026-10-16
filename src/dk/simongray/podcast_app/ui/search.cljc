(ns dk.simongray.podcast-app.ui.search
  "The views of the Search page: the form, the status line of the search
  and its results, which the listener can subscribe to.

  The state holds each search under its query in :searches, as
  dk.simongray.podcast-app.search describes it, the feed URLs that are
  being subscribed to in :subscribing, and the errors of those that
  failed by URL in :subscribe-errors."
  (:require [clojure.string :as str]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]))

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

(defn subscription-status
  "Where the subscription to the feed at `url` stands in the `state`, with
  the `subscribed` URLs: :subscribed, :subscribing, :ready, or :waiting
  while the library is restored."
  [state subscribed url]
  (cond
    (nil? subscribed)                          :waiting
    (search/subscribed? subscribed url)        :subscribed
    (contains? (:subscribing state) url)       :subscribing
    :else                                      :ready))

;; WCAG 2.2, SC 2.5.3 Label in Name: the name that a screen reader hears
;; starts with the visible label, e.g. "Subscribe to Podcasting 2.0". The
;; button is aria-disabled rather than disabled, so that it keeps the focus
;; as it changes.
(defn subscribe-button
  "The button that subscribes to the show `title` at `url`, at the
  subscription `status`."
  [status url title]
  [:button (cond-> {:type "button"}
             (not= :ready status) (assoc :aria-disabled "true")
             (= :ready status)    (assoc :on {:click [[:library/subscribe url title]]}))
   (case status
     :subscribed  "Subscribed"
     :subscribing "Subscribing…"
     "Subscribe")
   [:span.visually-hidden " to " title]])

;; W3C, Images tutorial, decorative images: the title next to the artwork
;; already names the show, so the artwork has an empty alt. A site often has
;; several feeds of one show, e.g. in MP3 and in Opus, so a title that
;; another result shares comes with the URL of its feed, which the name of
;; the button has too (WCAG 2.2, SC 2.4.6 Headings and Labels).
(defn result
  "A show that the search found, the `result`, at the subscription
  `status`, with the `error` of a subscription that failed. When
  `shared-title?`, it shows the URL of its feed."
  [{:keys [url title author artwork] :as result} status error shared-title?]
  (let [title (or title url)]
    [:article
     (when artwork
       [:img {:src artwork :alt "" :width 64 :height 64 :loading "lazy"}])
     [:h2 title]
     (when author
       [:p author])
     (when shared-title?
       [:p url])
     (subscribe-button status url (cond-> title shared-title? (str ", " url)))
     (when error
       [:p "Couldn't subscribe: " (:message error)])]))

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
          (result r
                  (subscription-status state subscribed url)
                  (get-in state [:subscribe-errors url])
                  (contains? shared title))])])))

(defn content
  "The content of the Search page of the `state`, below its heading."
  [state]
  (let [q      (query state)
        search (get-in state [:searches q])]
    (list (search-form q)
          (status-line q search)
          (results state search))))
