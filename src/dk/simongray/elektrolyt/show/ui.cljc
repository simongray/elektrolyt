(ns dk.simongray.elektrolyt.show.ui
  "The views of the Show page: the show and its subscription, its episodes
  a page at a time, the shows that it links to, and the picks like it.

  The page shows the show's search result at once, and its episodes as
  its feed arrives."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.spec :as spec]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.linked :as linked]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.details :as details]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.subscription :as subscription]
            [dk.simongray.elektrolyt.ui.text :as text]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn guid
  "The guid of the show on the Show page in `state`."
  [state]
  (get-in state [:route :path-params :guid]))

(defn source
  "The feed of the show on the Show page in `state`, or nil."
  [state]
  (show/source state (guid state)))

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

;; The categories are in the Details alone, so that the line under the
;; title doesn't repeat them
(defn facts
  "The facts of the show `page` for the line under its title: its medium
  when it isn't a podcast, and whether it's complete, explicit or made
  with AI."
  [{:keys [medium complete? explicit? ai-content?]}]
  [(when (and (spec/mediums medium) (not= "podcast" medium))
     (str/capitalize medium))
   (when complete? "Complete")
   (when explicit? "Explicit")
   (when ai-content? "Made with AI")])

;; The order of Apple's Information and Pocket Casts' details: what the
;; show is and where to find it first, then when it publishes, and the
;; fine print last. Like Wikipedia's infoboxes, the rows keep to key
;; facts, and the count of episodes is left to their list.
;; https://en.wikipedia.org/wiki/Wikipedia:Manual_of_Style/Infoboxes
(defn details-rows
  "The rows of the Details of the show `page`, without its website and
  feed, which the line under its title links to."
  [{:keys [funding categories language latest serial? locations license copyright]
    :as   page}]
  (let [names (distinct (map last categories))]
    (concat
     [["Support" (details/support funding)]
      [(if (next names) "Categories" "Category") names]
      ["Language" [(some-> language text/language-name)]]
      ["Latest episode" [(some-> latest time-ui/date)]]
      ["Schedule" [(schedule-text page)]]
      ["Order" [(when serial? "Serial, from the first episode")]]]
     (details/location-rows locations)
     [["License" [(details/license license)]]
      ["Copyright" [copyright]]])))

;; Fewer rows in the Details: one word links to the show's website and one
;; to its feed, for another app to subscribe to, with the feed icon in
;; place of the new-tab arrow. The author stays text, since it's often the
;; show's own name, which links to the show from its episodes.
(defn byline
  "The line under the title of the show `page`, whose feed is at `url`: its
  author, its facts, and links to its website and its feed."
  [{:keys [author link] :as page} url]
  (facts-ui/line
   (concat [author]
           (facts page)
           [(details/web-link link "Website")
            (some-> url (details/web-link "Feed" icons/rss))])))

(defn episode-row
  "The episode `row` in the list of episodes in `state`: its artwork, or
  else the `show`'s, and its title in the language `lang`, both linking to
  `href`, with its date, duration and source."
  [state href {:keys [title published duration supplement] :as row} lang show]
  [:article.show-episode
   (lists-ui/linked-row href (lists-ui/thumbnail state (if (:artwork row) row show))
                        :h3 title lang
                        [(some-> published time-ui/date)
                         (some-> duration time-ui/duration)
                         (facts-ui/source supplement)])])

(defn status-text
  "The text of the episodes' status line for the feed's load `status`, with
  `n` episodes so far, the `error` of a failed fetch and the count of
  `newer` episodes that wait."
  [status n error newer]
  (case status
    :loading "Loading episodes…"
    :failed  (str "The feed couldn't be fetched. " (:message error))
    (str (text/counted n "episode") "."
         (when newer
           (str " " (text/counted newer "new episode") (if (= 1 newer) " is in." " are in."))))))

;; A button that lists more, rather than a list that grows as it scrolls,
;; whose end a keyboard or a voice can't reach (GitLab's audit of infinite
;; scrolling, issue 1196 of gitlab-ui, and Deque on role=feed). New
;; episodes that arrive while the list shows wait for a button, as in the
;; offline cookbook, so that the list doesn't move by itself.
;; https://web.dev/articles/offline-cookbook#cache-then-network
;;
;; A show in the library is there whole, so a load of its feed from before
;; the subscription, e.g. a prefetch, doesn't apply to it
(defn episodes
  "The episodes of the show `page` in `state`, a page at a time, with the
  status of the load and buttons for newer episodes that wait and for
  more."
  [state {rows :episodes :keys [language] :as page}]
  (let [g                             (guid state)
        {:keys [key url error newer]} (source state)
        url                           (when-not key url)
        status                        (show/status state g)
        shown                         (get-in state [:episodes-shown g] show/page-size)
        newer                         (when-not key (:count newer))
        lang                          (text/lang language)]
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
           (episode-row state (routes/episode-href (or key g) (:guid row) (:title row) url) row lang page)])])
     (when (< shown (count rows))
       [:button {:type "button"
                 :on   {:click [[:show/more g (count rows)]]}}
        "Show more episodes"])]))

(def looking-up
  "The status line of a show or an episode page during a lookup in the
  Podcast Index."
  [:p {:role "status"} "Looking for the show in the Podcast Index…"])

(def not-found
  "The content of a show's page when neither the library, the link to it
  nor the Podcast Index gives its feed."
  [:p "This show isn't in the library, and the link to it doesn't say "
   "where its feed is. Find it with "
   [:a {:href (routes/href :route/search)} "Search"]
   " instead."])

;; HTML, header and hgroup: the introduction of the show's article, its
;; heading first, with its byline as its subtitle, and its description as
;; its summary
;; https://html.spec.whatwg.org/multipage/sections.html#the-header-element
;; https://html.spec.whatwg.org/multipage/sections.html#the-hgroup-element
(defn intro
  "The header of the article of the show `page` in `state`: the `heading`
  and byline, the artwork with the subscription beside it, and the
  description in the show's language."
  [state heading {:keys [title notes language] :as page}]
  (let [url (:url (source state))]
    [:header.show-intro
     [:hgroup heading (byline page url)]
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
;; links there too, for a pointer alone, as in the Library's shows
;; https://inclusive-components.design/cards/
(defn show-cards
  "The list of the show `cards` in `state`, each with its artwork or a
  blank, and its title, both linking to its page."
  [state cards]
  [:ul.linked-shows {:role "list"}
   (for [{show-guid :guid :keys [title url] :as card} cards]
     (let [href  (routes/show-href show-guid title url)
           label (or title "Untitled show")]
       [:li {:replicant/key show-guid}
        [:article.linked-show
         (or (artwork/linked href label (artwork/img card 96 (merge {:loading "lazy"}
                                                                    (colors/placeholder state card))))
             [:div.no-artwork])
         [:h3 [:a {:href href} label]]]]))])

;; Pocket Casts puts a show's podroll first among its recommendations, as
;; "Recommended shows by the creator", and Apple Podcasts ends a show's
;; page with More From its publisher.
;; https://support.pocketcasts.com/knowledge-base/podroll-podcast-recommendations/
;; https://podcasts.apple.com/us/podcast/the-daily/id1200361736
;; https://podcasting2.org/docs/podcast-namespace/tags/podroll
(defn linked-shows
  "The shows that the show `title` on the Show page in `state` links to:
  its podroll, and its publisher's other shows, or nil when its feed
  `source` links to none."
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
  "The section of picks under a show: the id and text of its heading, the
  lead of the line on why they came up, and the text when there are
  none."
  {:id "show-picks" :heading "More like this show" :lead "About"
   :none "Nothing in your library is like it yet."})

(defn content
  "The content of the Show page in `state`: the show's article, with the
  `heading` in its header and the picks after its Details, and the shows
  that it links to, or else the `heading` and the status of the show."
  [state heading]
  (let [page   (show/page state (guid state))
        status (show/status state (guid state))]
    (if-not page
      (list heading
            (case status
              :looking-up looking-up
              :loading    [:p {:role "status"} "Loading the show…"]
              :failed     [:p {:role "status"} (status-text status 0 (:error (source state)) nil)]
              not-found))
      (let [aside (details/aside (details-rows page) (:people page))
            src   (source state)]
        (list
         (details/article
          {}
          [(intro state heading page)
           (episodes state page)]
          aside
          (picks-ui/folded state picks-section [:show (guid state)] #(picks/like-show state (guid state))))
         (when (:feed src)
           (linked-shows state (:title page) src)))))))
