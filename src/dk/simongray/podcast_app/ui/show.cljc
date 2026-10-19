(ns dk.simongray.podcast-app.ui.show
  "The views of the Show page: the show, the button that subscribes to it,
  its episodes, a page of them at a time, the shows that it links to, and
  the picks like it.

  The page comes from the best source at hand, as
  dk.simongray.podcast-app.show describes it, so it shows what a search
  knew of the show at once, and its episodes as its feed arrives."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.spec :as spec]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.linked :as linked]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.text :as text]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.details :as details]
            [dk.simongray.podcast-app.ui.facts :as facts-ui]
            [dk.simongray.podcast-app.ui.picks :as picks-ui]
            [dk.simongray.podcast-app.ui.subscription :as subscription]
            [dk.simongray.podcast-app.ui.time :as time-ui]))

(defn guid
  "The guid of the show of the Show page of the `state`."
  [state]
  (get-in state [:route :path-params :guid]))

(defn feed-url
  "The URL of the feed of the show of the Show page of the `state`, from
  show/feed-url, or nil."
  [state]
  (show/feed-url state (guid state)))

(defn library-key
  "The key that the library of the `state` keeps the show of the Show page
  under, or nil when it doesn't keep it."
  [state]
  (some-> (:library state) (show/library-key (guid state) (feed-url state))))

;; A show that the library keeps is in it whole, so a load of its feed from
;; before the subscription, e.g. the preview of a link, says nothing of it,
;; only how many episodes the page lists
(defn loaded
  "What the `state` keeps of the load of the show of the Show page, which
  dk.simongray.podcast-app.show describes, or of a show that the library
  keeps, only how many episodes are :shown."
  [state]
  (cond-> (get-in state [:shows (guid state)])
    (library-key state) (select-keys [:shown])))

(defn cadence-text
  "How often a show publishes by its cadence of `ms` between episodes, in
  words, e.g. \"About every 2 weeks\"."
  [ms]
  (let [days   (Math/round (/ ms 86400000.0))
        weeks  (Math/round (/ days 7.0))
        months (Math/round (/ days 30.44))]
    (cond
      (zero? days) "More than once a day"
      (= 1 days)   "About every day"
      (< days 7)   (str "About every " days " days")
      (= 1 weeks)  "About every week"
      (< days 28)  (str "About every " weeks " weeks")
      (= 1 months) "About every month"
      :else        (str "About every " months " months"))))

(defn schedule-text
  "The schedule of the show `page`: the one that it states, or else how
  often it publishes, unless it's complete."
  [{:keys [update-frequency complete? cadence]}]
  (or update-frequency
      (when (and cadence (not complete?))
        (cadence-text cadence))))

;; Apple shows the primary category of a show, or its subcategory when it
;; has one, and Pocket Casts shows it under the title too
;; https://podcasters.apple.com/support/1691-apple-podcasts-categories
(defn facts
  "The facts of the show `page` for the line under its title: its first
  category, its medium when it isn't a podcast, and whether it's complete,
  explicit or made with AI."
  [{:keys [categories medium complete? explicit? ai-content?]}]
  [(last (first categories))
   (when (and (spec/mediums medium) (not= "podcast" medium))
     (str/capitalize medium))
   (when complete? "Complete")
   (when explicit? "Explicit")
   (when ai-content? "Made with AI")])

;; The order of Apple's Information and Pocket Casts' details: what the
;; show is and where to find it first, then when it publishes, and the
;; fine print last. Wikipedia's infoboxes keep to key facts, and so does
;; this, with the count of episodes left to the list of them.
;; https://en.wikipedia.org/wiki/Wikipedia:Manual_of_Style/Infoboxes
(defn details-rows
  "The rows of the Details of the show `page`, whose feed is at `url`."
  [{:keys [link funding categories language latest serial? locations license copyright]
    :as   page}
   url]
  (let [names (distinct (map last categories))]
    (concat
     [["Website" [(details/web-link link nil)]]
      ["Support" (details/support funding)]
      [(if (next names) "Categories" "Category") names]
      ["Language" [(some-> language text/language-name)]]
      ["Latest episode" [(some-> latest time-ui/date)]]
      ["Schedule" [(schedule-text page)]]
      ["Order" [(when serial? "Serial, from the first episode")]]]
     (details/location-rows locations)
     [["License" [(details/license license)]]
      ["Copyright" [copyright]]
      ["Feed" [(details/url-link url)]]])))

(defn episode
  "An episode in the list of episodes, the `row` of its :title, its date of
  :published and its :duration in seconds, whose title in the language
  `lang` links to the `href` of its page, beside its artwork, or else the
  `show`'s, which links there too."
  [href {:keys [title published duration] :as row} lang show]
  [:article
   (artwork/linked href (artwork/img (if (:artwork row) row show) 48 {:loading "lazy"}))
   [:h3 [:a {:href href :lang lang} title]]
   (facts-ui/line [(some-> published time-ui/date)
                   (some-> duration time-ui/duration)])])

(defn status-text
  "What the status line of the episodes says, at the `status` of the load of
  the feed, with `n` episodes so far, the `error` of a fetch that failed and
  the count of `newer` episodes that wait."
  [status n error newer]
  (case status
    (:opening :previewing :previewed :loading) "Loading episodes…"
    :failed (str "The feed couldn't be fetched. " (:message error))
    (str (text/counted n "episode") "."
         (when newer
           (str " " (text/counted newer "new episode") (if (= 1 newer) " is in." " are in."))))))

;; A button that lists more, rather than a list that grows as it scrolls,
;; which a keyboard or a voice can't reach the end of (GitLab's audit of
;; infinite scrolling, issue 1196 of gitlab-ui, and Deque on role=feed).
;; New episodes that arrive while the list shows wait for a button, as the
;; offline cookbook has it, so that the list doesn't move by itself.
;; https://web.dev/articles/offline-cookbook#cache-then-network
(defn episodes
  "The episodes of the show `page` of the Show page of the `state`, as many
  of them as its load has :shown, with the status of the load and a button
  for the newer episodes that wait. Each title links to the page of its
  episode."
  [state {rows :episodes :keys [language] :as page}]
  (let [g                                  (guid state)
        k                                  (library-key state)
        url                                (when-not k (feed-url state))
        {:keys [status shown error newer]} (loaded state)
        shown                              (or shown show/page-size)
        newer                              (:count newer)
        lang                               (text/lang language)]
    [:section {:aria-labelledby "episodes"}
     [:h2#episodes {:tabindex -1} "Episodes"]
     [:p {:role "status"} (status-text status (count rows) error newer)]
     (when newer
       [:button {:type "button" :on {:click [[:show/newer g]]}}
        (str "Show the " (text/plural newer "new episode"))])
     (when (seq rows)
       [:ol.episodes {:role "list"}
        (for [row (take shown rows)]
          [:li {:replicant/key (:guid row)}
           (episode (routes/episode-href (or k g) (:guid row) (:title row) url) row lang page)])])
     (when (< shown (count rows))
       [:button {:type "button"
                 :on   {:click [[:show/more g (count rows)]]}}
        "Show more episodes"])]))

(def looking-up
  "What the page of a show or an episode says while the Podcast Index is
  asked where the show's feed is."
  [:p {:role "status"} "Looking for the show in the Podcast Index…"])

(def not-found
  "The content of the page of a show that neither the library, the link to
  it nor the Podcast Index says where to find."
  [:p "This show isn't in the library, and the link to it doesn't say "
   "where its feed is. Find it with "
   [:a {:href (routes/href :route/search)} "Search"]
   " instead."])

;; HTML, header and hgroup: the introduction of the show's article, its
;; heading first, with its author as its subtitle, and its description as
;; its summary
;; https://html.spec.whatwg.org/multipage/sections.html#the-header-element
;; https://html.spec.whatwg.org/multipage/sections.html#the-hgroup-element
(defn intro
  "The header of the article of the show `page` of the Show page of the
  `state`: its `heading` with its author and facts in a line below it, its
  artwork, and beside it its subscription, and its description below, in
  the show's language."
  [state heading {:keys [title author notes language] :as page}]
  (let [url (feed-url state)]
    [:header.show-intro
     [:hgroup heading (facts-ui/line (cons author (facts page)))]
     (artwork/img page 256 (merge {:fetchpriority "high" :sizes artwork/intro-sizes}
                                  (colors/placeholder state page)))
     [:div.show-intro-body
      (when url
        (let [status (subscription/status state
                                          (search/subscribed-urls (:library state))
                                          url)]
          (list [:div.show-subscription
                 (subscription/button status url title true)]
                (subscription/error state url))))]
     (when notes
       [:div.show-notes {:lang (text/lang language)} notes])]))

;; Inclusive Components, Cards: the title is the link, and the artwork
;; beside it decoration, as in the Library's shows
;; https://inclusive-components.design/cards/
(defn show-cards
  "The list of the show `cards` of linked/card in the `state`, each its
  artwork, or a blank in its place, and its title, which links to its
  page."
  [state cards]
  [:ul.linked-shows {:role "list"}
   (for [{show-guid :guid :keys [title url] :as card} cards]
     [:li {:replicant/key show-guid}
      [:article.linked-show
       (or (artwork/img card 96 (colors/placeholder state card))
           [:div.no-artwork])
       [:h3 [:a {:href (routes/show-href show-guid title url)} (or title "Untitled show")]]]])])

;; Pocket Casts puts a show's podroll first among its recommendations, as
;; "Recommended shows by the creator", and Apple Podcasts ends a show's
;; page with More From its publisher. The shows are fetched as their part
;; of the page mounts.
;; https://support.pocketcasts.com/knowledge-base/podroll-podcast-recommendations/
;; https://podcasts.apple.com/us/podcast/the-daily/id1200361736
;; https://podcasting2.org/docs/podcast-namespace/tags/podroll
(defn linked-shows
  "The shows that the show of the Show page of the `state`, titled `title`,
  links to: those of its podroll, and the other shows of its publisher,
  or nil when its feed `source`, as episode/source gives it, links to
  none."
  [state title {:keys [feed url] :as source}]
  (let [show-guid                   (guid state)
        {:keys [podroll publisher]} (get-in state [:linked show-guid])]
    (when (linked/links? feed)
      [:div.linked {:replicant/key      show-guid
                    :replicant/on-mount [[:show/fetch-linked show-guid feed url]]}
       (when (seq podroll)
         [:section {:aria-labelledby "podroll"}
          [:h2#podroll (str "Recommended by " title)]
          (show-cards state podroll)])
       (when publisher
         [:section {:aria-labelledby "publisher"}
          [:h2#publisher (str "More from " (or (:title publisher) "its publisher"))]
          (show-cards state (:shows publisher))])])))

(def picks-section
  "The section of the picks under a show: its heading's id and text, and
  the lead of its line of why."
  {:id "show-picks" :heading "More like this show" :lead "About"})

(defn content
  "The content of the Show page of the `state` under its `heading`: an
  article, whose header has the heading, with the picks like it after its
  Details, and the shows that it links to, or else the heading and what
  became of the show."
  [state heading]
  (let [page                  (show/page state (guid state))
        {:keys [status error]} (loaded state)]
    (if-not page
      (list heading
            (case status
              :looking-up                     looking-up
              (:opening :previewing :loading) [:p {:role "status"} "Loading the show…"]
              :failed                         [:p {:role "status"} (status-text status 0 error nil)]
              not-found))
      (let [aside  (details/aside (details-rows page (feed-url state)) (:people page))
            source (episode/source state (guid state))]
        (list
         (details/article
          {}
          [(intro state heading page)
           (episodes state page)]
          aside
          (picks-ui/section state picks-section (picks/like-show state (guid state))))
         (when source
           (linked-shows state (:title page) source)))))))
