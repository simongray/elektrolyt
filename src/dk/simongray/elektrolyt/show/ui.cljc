(ns dk.simongray.elektrolyt.show.ui
  "The views of the Show page: the show and its subscription, its episodes
  a page at a time, the shows that it links to, and the picks like it.

  The page shows the show's search result at once, and its episodes as
  its feed arrives."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.spec :as spec]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.library.settings.ui :as settings-ui]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.linked :as linked]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.details :as details]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.loading :as loading]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.subscription :as subscription]
            [dk.simongray.elektrolyt.ui.text :as text]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn guid
  "The guid of the show on the Show or Episode page in `state`."
  [state]
  (show/route-guid state))

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
;; facts, and the count of episodes is left to their list. A serial's
;; order is the listener's place in it, once its whole feed is there.
;; https://en.wikipedia.org/wiki/Wikipedia:Manual_of_Style/Infoboxes
(defn details-rows
  "The rows of the Details of the show `page`, without its website and
  feed, which the line under its title links to, with the `order` that it's
  heard in, if any."
  [{:keys [funding categories language latest locations license copyright]
    :as   page}
   order]
  (let [names (distinct (map last categories))]
    (concat
     [["Support" (details/support funding)]
      [(if (next names) "Categories" "Category") names]
      ["Language" [(some-> language text/language-name)]]
      ["Latest episode" [(some-> latest time-ui/date)]]
      ["Schedule" [(schedule-text page)]]
      ["Order" [order]]]
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

(defn at-least
  "The `length` of some episodes, marked as at least that when the count
  `unknown` of them have no length, e.g. At least 9h 12m."
  [unknown length]
  (if (pos? unknown) (list "At least " length) length))

(defn place-facts
  "The place `p` of the listener in a show heard in order, in words, some of
  which can be nil: Serial · 19 episodes · 14h 45m until they begin, Season 2
  · Episode 2 of 3 · 1h left with the episode linked to `href`, its page, and
  19 episodes · All heard at the end. A show of one season doesn't name it."
  [{:keys [total left unknown started? seasons] :as p} href]
  (cond
    (not started?)
    ["Serial"
     (text/counted total "episode")
     (when (< unknown total)
       (at-least unknown (time-ui/duration left)))]

    (:next p)
    (let [{:keys [number total heard left unknown] :as season} (:season p)
          unheard                                              (- total heard)]
      [(when (< 1 seasons)
         (or (:name season) (some->> (:season season) (str "Season "))))
       (cond->> (str "Episode " number " of " total)
         href (vector :a {:href href}))
       (if (= unknown unheard)
         (str (text/counted unheard "episode") " left")
         (at-least unknown (conj (time-ui/duration left) " left")))])

    :else
    [(text/counted total "episode") "All heard"]))

(defn episode-href
  "The path of the page of the episode `row` of the show `show-guid`, with
  the feed's `url` for a show outside the library, or nil for an item
  with neither a guid nor an enclosure, which has no page."
  [show-guid {:keys [guid title] :as row} url]
  (when guid
    (routes/episode-href show-guid guid title url)))

;; The place links to the next episode, which the list from the first can
;; have far down
(defn order
  "The Order row of the Details of the show `page` in `state`, or nil. It's
  the listener's place if they hear it in order, with its episode linked to
  its page, or else that it's a serial while its whole feed is on its way."
  [state {:keys [serial?]}]
  (let [{:keys [key url whole?] :as src} (source state)]
    (if-let [p (show/place (:library state) src)]
      (let [href (episode-href (or key (guid state)) (show/next-row p) (when-not key url))]
        (interpose facts-ui/separator (remove nil? (place-facts p href))))
      (when (and serial? (not whole?))
        "Serial, from the first episode"))))

;; Apple and Pocket Casts keep a show's order with the show
;; https://support.apple.com/guide/podcasts/choose-podcast-and-episode-settings-pod48cadb787/mac
;; https://support.pocketcasts.com/knowledge-base/episode-sorting/
(defn settings-section
  "The folded settings of the show in `state`, when it's in the library,
  since the library keeps settings for its own shows alone, or nil."
  [{lib :library :as state}]
  (let [{:keys [key] :as src} (source state)]
    (when key
      (controls/folded
       state
       {:id "show-settings" :heading "Settings for this show" :place [:show-settings key]}
       #(let [in-order? (some? (show/heard-in-order lib src))]
          (list
           (settings-ui/choice "show-order" "Episodes" show/order-choices
                               (if in-order? :from-first :newest)
                               [:show/order key [:event/target-keyword]])
           (when in-order?
             (settings-ui/action-switch "hide-later" "Hide later episodes"
                                        (str "The list stops at your next episode, so that later "
                                             "titles don't give the story away.")
                                        (show/hide-later? lib key)
                                        [:show/hide-later key [:event/target-checked]]))))))))

;; The later titles stay out of the page rather than being blurred or
;; covered, so that a screen reader or a browser's translation doesn't give
;; them away either
(defn listed-to
  "How many of the `rows` of the show in `state` its list goes up to, while
  the listener keeps those after their next episode out of it, or nil."
  [{lib :library :as state} rows]
  (let [{:keys [key] :as src} (source state)]
    (when (and (show/hide-later? lib key) (not (controls/open? state [:later key])))
      (when-let [{next-guid :guid} (show/next-row (show/place lib src))]
        (some-> (first (keep-indexed #(when (= next-guid (:guid %2)) %1) rows)) inc)))))

(defn episode-row
  "The episode `row` in the list of episodes in `state`: its artwork, or
  else the `show`'s, and its title in the language `lang`, both linking to
  `href` when there is one, with its date, length and source. A trailer or
  a bonus says so, since the list puts the trailers first. By its :entry in
  the profile, a started row says what's left of it, and a played one is in
  a lighter ink and says so."
  [state href {:keys [type title published duration supplement entry] :as row} lang show]
  (let [img     (lists-ui/thumbnail state (if (:artwork row) row show))
        played? (:played? entry)
        facts   [(case type "trailer" "Trailer" "bonus" "Bonus" nil)
                 (some-> published time-ui/date)
                 (facts-ui/time-left (or (:duration entry) duration) entry)
                 (facts-ui/source supplement)
                 (when played? "Played")]]
    [(if played? :article.show-episode.played :article.show-episode)
     (if href
       (lists-ui/linked-row href img :h3 title lang facts)
       ;; the focus can go to the title, as to a link's, e.g. from Show more
       (list img
             [:h3 (cond-> {:tabindex -1} lang (assoc :lang lang)) title]
             (facts-ui/line facts)))]))

(defn failure-text
  "Why a feed couldn't be fetched, by the `error` of its fetch, in words
  for the listener rather than the library's own, which are about
  refreshes."
  [{:keys [id status]}]
  (case id
    :fetch/status             (str "Its server answered with an error, " status ".")
    :fetch/unreachable        "Its server couldn't be reached."
    :fetch/malformed          "What its server sent isn't a podcast feed."
    :fetch/unsupported-format "It's an Atom or RSS 1.0 feed, which can't be read."
    nil))

(defn status-text
  "The text of the episodes' status line for the feed's load `status`, with
  `n` episodes so far and the `error` of a failed fetch."
  [status n error]
  (case status
    :loading "Loading episodes…"
    :failed  (str/join " " (remove nil? ["The feed couldn't be fetched."
                                         (failure-text error)
                                         (when (pos? n) (str (text/counted n "episode") " from before."))]))
    (str (text/counted n "episode") ".")))

;; The button goes as the feed is fetched again, so the focus moves to the
;; heading at `refocus`
(defn failure
  "The notice that the feed of the show in `state` couldn't be fetched, by
  the `error` of its fetch, with the `n` episodes from before, and a button
  to try again that gives the focus to the heading at `refocus`."
  [state n error refocus]
  (notices/line {}
                (status-text :failed n error)
                [controls/button {:icon         icons/arrows-clockwise
                                  :data-refocus refocus
                                  :on           {:click [[:show/retry (guid state) (:url (source state))]]}}
                 "Try again" [:span.visually-hidden " to fetch the feed"]]))

(def skeleton-rows
  "Rows in the place of a show's episodes while they load."
  (loading/rows 5))

;; A button that lists more, rather than a list that grows as it scrolls,
;; whose end a keyboard or a voice can't reach (GitLab's audit of infinite
;; scrolling, issue 1196 of gitlab-ui, and Deque on role=feed). It goes
;; once it has done its part, so the focus moves on to the first episode
;; that the last page listed. New episodes that a fetch finds while the
;; list shows go in at once (show/refetched).
(defn more-button
  "A button labelled `label` that lists more episodes by the `actions`, and
  gives the focus to the one after the first `n`."
  [n actions label]
  [:button {:type         "button"
            :data-refocus (str "ol.episodes > li:nth-child(" (inc n) ") :is(h3 a, h3[tabindex])")
            :on           {:click actions}}
   label])

;; A show in the library is there whole, so a load of its feed from before
;; the subscription, e.g. a prefetch, doesn't apply to it.
(defn episodes
  "The episodes of the show `page` in `state`, a page at a time, with the
  status of the load, a button for more, and one for the later episodes
  while the listener keeps those out of the list."
  [state {rows :episodes n :episode-count :keys [language] :as page}]
  (let [g                               (guid state)
        {:keys [key url error] :as src} (source state)
        feed-id                         (show/feed-id src)
        url                             (when-not key url)
        status                          (show/status state g)
        shown                           (get-in state [:episodes-shown g] show/page-size)
        lang                            (text/lang language)
        entry-of                        #(player/entry (:library state) [feed-id (:guid %)])
        listed-to                       (listed-to state rows)
        total                           (or listed-to n)]
    [:section {:aria-labelledby "episodes"}
     [:h2#episodes {:tabindex -1} "Episodes"]
     (if (= :failed status)
       (failure state n error "#episodes")
       [(if (= :loading status) :p.visually-hidden :p) {:role "status"} (status-text status n error)])
     (when (and (= :loading status) (zero? n))
       skeleton-rows)
     (when (seq rows)
       [:ol.episodes {:role "list"}
        (for [[i row] (map-indexed vector (take (min shown total) rows))]
          [:li {:replicant/key (or (:guid row) i)}
           (episode-row state (episode-href (or key g) row url) (assoc row :entry (entry-of row)) lang page)])])
     (cond
       (< shown total)
       (more-button shown [[:show/more g total]] "Show more episodes")

       (and listed-to (< listed-to n))
       (more-button listed-to
                    [[:store/assoc-in [:open [:later key]] true]
                     [:store/assoc-in [:episodes-shown g] n]]
                    (str "Show " (text/counted (- n listed-to) "later episode"))))]))

(def looking-up
  "The status line of a show or an episode page during a lookup in the
  Podcast Index."
  (loading/status "Looking for the show in the Podcast Index…"))

(def not-found
  "The content of a show's page when neither the library, the link to it
  nor the Podcast Index gives its feed."
  [:p "This show isn't in the library, and the link to it doesn't say "
   "where its feed is. Find it with "
   [:a {:href (routes/href :route/search)} "Search"]
   " instead."])

;; HTML, header and hgroup: the introduction of the show's article, its
;; bookmark and heading first, with its byline as its subtitle, and its
;; description as its summary
;; https://html.spec.whatwg.org/multipage/sections.html#the-header-element
;; https://html.spec.whatwg.org/multipage/sections.html#the-hgroup-element
(defn intro
  "The header of the article of the show `page` in `state`: the show's
  bookmark before the `heading` and byline, the artwork, the error of a
  subscription that failed, and the description in the show's language."
  [state heading {:keys [title notes language] :as page}]
  (let [url (:url (source state))]
    [:header.show-intro
     [:div.with-bookmark
      (when url
        (subscription/button (subscription/status state (search/subscribed-urls (:library state)) url)
                             url title))
      [:hgroup heading (byline page url)]]
     (artwork/img page 256 (merge {:fetchpriority "high" :sizes artwork/intro-sizes :under true}
                                  (colors/placeholder state page)))
     (when-let [error (some->> url (subscription/error state))]
       [:div.show-intro-body error])
     (when notes
       [:div.show-notes {:lang (text/lang language)} notes])]))

;; Inclusive Components, Cards: the title is the link, and the artwork
;; links there too, for a pointer alone, as in the Library's shows
;; https://inclusive-components.design/cards/
(defn show-cards
  "The list of the show `cards` in `state`, each with its artwork or a
  blank, and its title, both linking to its page, after the mark of a show
  that's subscribed."
  [state cards]
  (let [subscribed (search/subscribed-urls (:library state))]
    [:ul.linked-shows {:role "list"}
     (for [{show-guid :guid :keys [title url] :as card} cards]
       (let [href  (routes/show-href show-guid title url)
             label (or title "Untitled show")]
         [:li {:replicant/key show-guid}
          [:article.linked-show
           (or (artwork/linked href label (artwork/img card 96 (merge {:loading "lazy"}
                                                                      (colors/placeholder state card)))
                               (when url {:on (show/prefetching show-guid url)}))
               [:div.no-artwork])
           [:h3 (cond->> [:a (cond-> {:href href}
                               url (assoc :on (show/prefetching show-guid url)))
                          label]
                  (= :subscribed (subscription/status state subscribed url show-guid))
                  (subscription/marked))]]]))]))

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

;; A subscription whose feed can't be fetched has no page, but the
;; listener can still leave it
(defn unsubscribe-button
  "The button that unsubscribes from the show on the Show page in `state`,
  or nil unless the library is subscribed to the feed of the page."
  [state]
  (let [lib (:library state)
        url (:url (source state))]
    (when-let [k (some-> lib (show/subscribed-key url))]
      (subscription/button :subscribed url (or (:title (show/subscription-of lib k)) url)))))

(def picks-section
  "The section of picks under a show: the id and text of its heading, the
  lead of the line on why they came up, and the text when there are
  none."
  {:id "show-picks" :heading "More like this show" :lead "About"
   :none "Nothing in your library is like it yet."})

(defn content
  "The content of the Show page in `state`: the show's article, with the
  `heading` in its header and the picks after its Details, and the shows
  that it links to, or else the `heading`, after the bookmark of a
  subscription whose feed failed, and the status of the show."
  [state heading]
  (let [page   (show/page state (guid state))
        status (show/status state (guid state))]
    (if-not page
      (list (if (= :failed status)
              [:div.with-bookmark (unsubscribe-button state) heading]
              heading)
            (case status
              :looking-up (list looking-up skeleton-rows)
              :loading    (list (loading/status "Loading the show…") skeleton-rows)
              :failed     (failure state 0 (:error (source state)) "main h1")
              not-found))
      (let [aside (details/aside (details-rows page (order state page)) (:people page) (settings-section state))
            src   (source state)]
        (list
         (details/article
          {}
          [(intro state heading page)
           (episodes state page)]
          aside
          (picks-ui/folded state picks-section [:show (guid state)] #(picks/fresh state [:show (guid state)])))
         (when (:feed src)
           (linked-shows state (:title page) src)))))))
