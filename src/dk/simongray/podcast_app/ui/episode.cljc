(ns dk.simongray.podcast-app.ui.episode
  "The views of the Episode page: the episode, the button that plays it, and
  its show notes, chapters and transcript, whose times play it from there.

  The page comes from the feed of its show at hand, as
  dk.simongray.podcast-app.episode describes it, so it shows at once when
  the start of the feed has the episode."
  (:require [dk.simongray.play-nice.media.fragment :as fragment]
            [dk.simongray.podcast.format.transcript :as transcript]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.show :as show-ui]
            [dk.simongray.wary-fetch.url :as url]))

(defn clock
  "The `seconds` into an episode as a time element, e.g. 12:34, whose
  datetime is the duration since its start."
  [seconds]
  [:time {:datetime (str "PT" (long seconds) "S")} (time/format-clock seconds)])

;; MDN: a link is for a real URL. Each time links to the page at ?t=, which
;; can be copied or opened in a tab of its own, and a click on it plays the
;; episode from there instead, as a time does in the description of a
;; YouTube video.
;; https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/a
(defn time-link
  "A link to the episode `page` at `seconds` into it, with the Hiccup
  `content`, which plays the episode from there."
  [page seconds content]
  [:a {:href (routes/episode-href (:guid page) (:item-guid page) (:title page)
                                  (:url page) seconds)
       :on   {:click [[:event/prevent-default]
                      [:player/play (episode/playable page) seconds]]}}
   content])

(defn route-page
  "The episode page of the Episode page of the `state`, or nil."
  [state]
  (let [{:keys [path-params slug]} (:route state)]
    (episode/page state (:guid path-params) (:hash path-params) slug time-link)))

(defn start-time
  "The seconds that the Episode page of the `state` starts the episode at,
  as the t of its link gives them, e.g. 73 for ?t=73 or ?t=1m13s, or nil."
  [state]
  (some->> (get-in state [:route :query-params :t])
           (str "?t=")
           (fragment/link-time)))

;; WAI-ARIA APG, button pattern: a button whose label changes with its
;; state, from Play to Pause, has no aria-pressed, which would say the
;; opposite. https://www.w3.org/WAI/ARIA/apg/patterns/button/
(defn play-label
  "What the button of the episode `page` says when it would play it, by the
  `entry` of the episode in the profile and the start time `t` of the link,
  e.g. \"Play from 1:13\", \"Resume, 12 min left\" or \"Play\"."
  [{:keys [duration] :as page} {:keys [position played?] :as entry} t]
  (cond
    t
    (str "Play from " (time/format-clock t))

    (and (number? position) (pos? position) (not played?))
    (if (number? duration)
      (str "Resume, " (show-ui/duration-text (max 0 (- duration position))) " left")
      "Resume")

    :else
    "Play"))

(defn play-button
  "The button that plays the episode `page`, from `t` seconds or from where
  its `entry` in the profile left it, or pauses it while the `player`
  plays it."
  [page player entry t]
  (if (and (= (:id page) (:id player)) (false? (get-in player [:media :paused])))
    [:button {:type "button" :on {:click [[:player/pause]]}} "Pause"]
    [:button {:type "button" :on {:click [[:player/play (episode/playable page) t]]}}
     (play-label page entry t)]))

;; The two places of Apple, Pocket Casts, Castro and Overcast: right after
;; the episode that plays, or at the end. Play next moves an episode that's
;; queued already.
(defn queue-buttons
  "The buttons that queue the episode `page`, by where it is in the queue,
  `queued`: nil, :queued, or :current, which needs none."
  [{:keys [title] :as page} queued]
  (let [playable (episode/playable page)]
    (when-not (= :current queued)
      (list
       [:button {:type "button" :on {:click [[:queue/add playable title true]]}}
        "Play next"]
       (if (= :queued queued)
         [:button {:type "button" :on {:click [[:queue/remove (:id page)]]}}
          "Remove from queue"]
         [:button {:type "button" :on {:click [[:queue/add playable title false]]}}
          "Add to queue"])))))

(defn intro
  "The show, the date, the duration and the artwork of the episode `page`,
  whether its `entry` in the profile is played, the button that plays it
  as the `player` and the start time `t` have it, and the buttons that
  queue it, by where it's `queued`."
  [{:keys [show published duration] :as page} player entry t queued]
  [:div.episode-intro
   (artwork/img page 160 {:fetchpriority "high"})
   [:p [:a {:href (:href show)} (:title show)]]
   (when (or published duration)
     [:p
      (when published
        [:time {:datetime (show-ui/date-attribute published)}
         (show-ui/date-text published)])
      (when (and published duration)
        [:span {:aria-hidden "true"} " · "])
      (when duration
        [:time {:datetime (str "PT" (long duration) "S")}
         (show-ui/duration-text duration)])
      (when (:played? entry)
        (list [:span {:aria-hidden "true"} " · "] "Played"))])
   [:div.episode-actions
    (play-button page player entry t)
    (queue-buttons page queued)]])

;; A chapters file is the publisher's, so a chapter's link is only one when
;; it goes to the web, never e.g. a javascript: URL
(defn chapters
  "The `chapters` of the episode `page` as a list of their times and titles,
  which link to the web page of a chapter that has one."
  [page chapters]
  [:section {:aria-labelledby "chapters"}
   [:h2#chapters "Chapters"]
   [:ol.chapters {:role "list"}
    (for [{:keys [start-time title url]} chapters]
      [:li {:replicant/key start-time}
       (time-link page start-time (clock start-time))
       " "
       (if (url/web-url? url)
         (links/new-tab [:a {:href url} (or title url)])
         title)])]])

;; WAI, Transcribing Audio to Text: the transcript is on the page of the
;; media itself, in paragraphs that name who speaks. It's behind a
;; disclosure, since an hour's transcript is ten thousand words, and it's
;; read once the listener opens it.
;; https://www.w3.org/WAI/media/av/transcripts/
(defn transcript
  "The transcript of the episode `page`, with the `files` read so far, in
  paragraphs of a speaker's turn or about a minute."
  [{:keys [id view] :as page} files]
  (let [{:keys [transcript-status]} files
        {:keys [document error]}    (:transcript files)]
    [:section {:aria-labelledby "transcript"}
     [:h2#transcript "Transcript"]
     [:details {:on {:toggle [[:episode/read-transcript id view]]}}
      [:summary "Show the transcript"]
      (cond
        (= :reading transcript-status)
        [:p {:role "status"} "Loading the transcript…"]

        (or error (and (= :read transcript-status) (empty? (:segments document))))
        [:p {:role "status"} "The transcript couldn't be read."]

        document
        [:div.transcript
         (for [{:keys [start-time speaker body]} (:segments (transcript/paragraphs document))]
           [:p
            (when start-time
              (list (time-link page start-time (clock start-time)) " "))
            (when speaker
              (list [:strong speaker] ": "))
            body])])]]))

(def not-found
  "The content of the page of an episode that its show's feed doesn't
  have."
  [:p "This episode isn't in its show's feed any more, "
   "or the link to it is out of date."])

(defn missing
  "What the Episode page of the `state` says while it has no episode to
  show: that its show or its feed is on the way, or that the episode isn't
  there, with a link to its show when the feed is at hand."
  [state]
  (let [{:keys [guid]} (get-in state [:route :path-params])]
    (case (episode/status state guid)
      :looking-up [:p {:role "status"} "Looking for the show in the Podcast Index…"]
      :loading    [:p {:role "status"} "Loading the episode…"]
      :no-show    show-ui/not-found
      :failed     [:p {:role "status"}
                   (show-ui/status-text :failed 0 (get-in state [:shows guid :error]) nil)]
      (list not-found
            [:p [:a {:href (routes/show-href guid nil (get-in state [:route :query-params :url]))}
                 "Go to the show"]
             " for its other episodes."]))))

(defn content
  "The content of the Episode page of the `state`, below its heading."
  [state]
  (if-let [{:keys [id notes view] :as page} (route-page state)]
    (let [files (get-in state [:episodes id])
          shown (episode/shown-chapters page files)]
      ;; the files are read once the page of the episode is in the document,
      ;; and again for the page of another one
      [:div {:replicant/key      (pr-str id)
             :replicant/on-mount [[:episode/read-files id view]]}
       (intro page (:player state) (player/entry (:library state) id) (start-time state)
              (cond
                (= id (:id (first (:episodes (some-> (:library state) queue/view))))) :current
                (queue/queued? state id)                                              :queued))
       (when (seq notes)
         [:section {:aria-labelledby "notes"}
          [:h2#notes "Show notes"]
          [:div.show-notes notes]])
       (when shown
         (chapters page shown))
       (when (:transcript view)
         (transcript page files))])
    (missing state)))
