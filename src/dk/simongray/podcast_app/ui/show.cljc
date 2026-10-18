(ns dk.simongray.podcast-app.ui.show
  "The views of the Show page: the show, the button that subscribes to it,
  and its episodes, a page of them at a time.

  The page comes from the best source at hand, as
  dk.simongray.podcast-app.show describes it, so it shows what a search
  knew of the show at once, and its episodes as its feed arrives."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.feed.spec :as spec]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.details :as details]
            [dk.simongray.podcast-app.ui.subscription :as subscription]))

(defn guid
  "The guid of the show of the Show page of the `state`."
  [state]
  (get-in state [:route :path-params :guid]))

(defn duration-text
  "The `seconds` of an episode as hours and minutes, e.g. \"1 h 23 min\",
  or as seconds when it's shorter than half a minute."
  [seconds]
  (let [minutes (quot (+ (long seconds) 30) 60)
        h       (quot minutes 60)
        m       (rem minutes 60)]
    (cond
      (zero? minutes) (str (long seconds) " s")
      (pos? h)        (str h " h" (when (pos? m) (str " " m " min")))
      :else           (str m " min"))))

(defn page-language
  "The language of the page, which its dates are written in."
  []
  #?(:clj  "en"
     :cljs (or (some-> js/globalThis .-document .-documentElement .-lang
                       not-empty)
               "en")))

(defn date-text
  "The date of the instant `inst`, as the language of the page writes it,
  e.g. \"Oct 16, 2026\"."
  [inst]
  #?(:clj  (str inst)
     :cljs (.toLocaleDateString inst (page-language) #js {:dateStyle "medium"})))

(defn date-attribute
  "The instant `inst` as the datetime of a time element."
  [inst]
  #?(:clj  (str (.toInstant ^java.util.Date inst))
     :cljs (.toISOString inst)))

(defn date
  "The date of the instant `inst` as a time element."
  [inst]
  [:time {:datetime (date-attribute inst)} (date-text inst)])

;; ECMA-402, Intl.DisplayNames: the name of a language in the language of
;; the page, which gives back the tag itself when it doesn't know it
(defn language-name
  "The name of the language `tag` in the language of the page, e.g.
  \"English\" for en, or the tag when the browser can't name it."
  [tag]
  #?(:clj  tag
     :cljs (try
             (.of (js/Intl.DisplayNames. #js [(page-language)] #js {:type "language"}) tag)
             (catch :default _ tag))))

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
  [{:keys [link categories language latest serial? locations license copyright]
    :as   page}
   url]
  (let [names (distinct (map last categories))]
    (concat
     [["Website" [(details/web-link link nil)]]
      [(if (next names) "Categories" "Category") names]
      ["Language" [(some-> language language-name)]]
      ["Latest episode" [(some-> latest date)]]
      ["Schedule" [(schedule-text page)]]
      ["Order" [(when serial? "Serial, from the first episode")]]]
     (details/location-rows locations)
     [["License" [(details/license license)]]
      ["Copyright" [copyright]]
      ["Feed" [(details/web-link url url)]]])))

;; HTML, section 4.5.14: the time element gives a machine-readable date, or
;; a duration such as PT5025S, besides the text that a reader sees. The dot
;; between them is decoration, which a screen reader skips.
(defn episode
  "An episode in the list of episodes, the `row` of its :title, its date of
  :published and its :duration in seconds, whose title links to the
  `href` of its page."
  [href {:keys [title published duration] :as row}]
  [:article
   [:h3 [:a {:href href} title]]
   (when (or published duration)
     [:p
      (when published
        [:time {:datetime (date-attribute published)} (date-text published)])
      (when (and published duration)
        [:span {:aria-hidden "true"} " · "])
      (when duration
        [:time {:datetime (str "PT" (long duration) "S")}
         (duration-text duration)])])])

(defn status-text
  "What the status line of the episodes says, at the `status` of the load of
  the feed, with `n` episodes so far, the `error` of a fetch that failed and
  the count of `newer` episodes that wait."
  [status n error newer]
  (case status
    (:opening :previewing :previewed :loading) "Loading episodes…"
    :failed (str "The feed couldn't be fetched. " (:message error))
    (str n (if (= 1 n) " episode." " episodes.")
         (when newer
           (str " " newer (if (= 1 newer) " new episode is in." " new episodes are in."))))))

;; A button that lists more, rather than a list that grows as it scrolls,
;; which a keyboard or a voice can't reach the end of (GitLab's audit of
;; infinite scrolling, issue 1196 of gitlab-ui, and Deque on role=feed).
;; New episodes that arrive while the list shows wait for a button, as the
;; offline cookbook has it, so that the list doesn't move by itself.
;; https://web.dev/articles/offline-cookbook#cache-then-network
(defn episodes
  "The episodes of the show `guid`, the `rows`, at the `status` of its load
  with the `error` of a fetch that failed, as many of them as `shown`, and
  a button for the count of `newer` episodes that wait. The link to each
  goes to what `href-fn` gives for its row."
  [guid href-fn rows status shown error newer]
  [:section {:aria-labelledby "episodes"}
   [:h2#episodes {:tabindex -1} "Episodes"]
   [:p {:role "status"} (status-text status (count rows) error newer)]
   (when newer
     [:button {:type "button" :on {:click [[:show/newer guid]]}}
      (if (= 1 newer) "Show the new episode" "Show the new episodes")])
   (when (seq rows)
     [:ol.episodes {:role "list"}
      (for [row (take shown rows)]
        [:li {:replicant/key (:guid row)} (episode (href-fn row) row)])])
   (when (< shown (count rows))
     [:button {:type "button"
               :on   {:click [[:show/more guid (count rows)]]}}
      "Show more episodes"])])

(def not-found
  "The content of the page of a show that neither the library, the link to
  it nor the Podcast Index says where to find."
  [:p "This show isn't in the library, and the link to it doesn't say "
   "where its feed is. Find it with "
   [:a {:href (routes/href :route/search)} "Search"]
   " instead."])

(defn content
  "The content of the Show page of the `state`, below its heading."
  [state]
  (let [g                                     (guid state)
        url                                   (show/feed-url state g)
        k                                     (some-> (:library state) (show/library-key g url))
        {:keys [title author notes] :as page} (show/page state g)
        {:keys [status shown error newer]}    (get-in state [:shows g])]
    (if-not page
      (case status
        :looking-up                     [:p {:role "status"} "Looking for the show in the Podcast Index…"]
        (:opening :previewing :loading) [:p {:role "status"} "Loading the show…"]
        :failed                         [:p {:role "status"} (status-text status 0 error nil)]
        not-found)
      (let [aside (details/aside (details-rows page url) (:people page))]
        (details/with-aside
          [(artwork/img page 160 {:fetchpriority "high"})
           (when author
             [:p author])
           (details/facts-line (facts page))
           (when aside
             details/link)
           (when url
             (let [status (subscription/status state
                                               (search/subscribed-urls (:library state))
                                               url)]
               (list (subscription/button status url title true)
                     (when (= :subscribed status)
                       (subscription/new-episodes (:library state) k))
                     (subscription/error state url))))
           (details/support (:funding page))
           (when notes
             [:div.show-notes notes])
           (episodes g
                     #(routes/episode-href (or k g) (:guid %) (:title %) (when-not k url))
                     (:episodes page) status (or shown show/page-size) error
                     (:count newer))]
          aside)))))
