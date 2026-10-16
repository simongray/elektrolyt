(ns dk.simongray.podcast-app.ui.show
  "The views of the Show page: the show, the button that subscribes to it,
  and its episodes, a page of them at a time.

  The page comes from the best source at hand, as
  dk.simongray.podcast-app.show describes it, so it shows what a search
  knew of the show at once, and its episodes as its feed arrives."
  (:require [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
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
  the feed, with `n` episodes so far and the `error` of a fetch that failed."
  [status n error]
  (case status
    (:previewing :previewed :loading) "Loading episodes…"
    :failed (str "The feed couldn't be fetched. " (:message error))
    (str n (if (= 1 n) " episode." " episodes."))))

;; A button that lists more, rather than a list that grows as it scrolls,
;; which a keyboard or a voice can't reach the end of (GitLab's audit of
;; infinite scrolling, issue 1196 of gitlab-ui, and Deque on role=feed)
(defn episodes
  "The episodes of the show `guid`, the `rows`, at the `status` of its load
  with the `error` of a fetch that failed, as many of them as `shown`. The
  link to each goes to what `href-fn` gives for its row."
  [guid href-fn rows status shown error]
  [:section {:aria-labelledby "episodes"}
   [:h2#episodes "Episodes"]
   [:p {:role "status"} (status-text status (count rows) error)]
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
        {:keys [status shown error]}          (get-in state [:shows g])]
    (if-not page
      (case status
        :looking-up          [:p {:role "status"} "Looking for the show in the Podcast Index…"]
        (:previewing :loading) [:p {:role "status"} "Loading the show…"]
        :failed              [:p {:role "status"} (status-text status 0 error)]
        not-found)
      (list (artwork/img page 160 {:fetchpriority "high"})
            (when author
              [:p author])
            (when url
              (list (subscription/button
                     (subscription/status state
                                          (search/subscribed-urls (:library state))
                                          url)
                     url
                     title)
                    (subscription/error state url)))
            (when notes
              [:div.show-notes notes])
            (episodes g
                      #(routes/episode-href (or k g) (:guid %) (:title %) (when-not k url))
                      (:episodes page) status (or shown show/page-size) error)))))
