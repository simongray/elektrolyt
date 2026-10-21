(ns dk.simongray.podcast-app.ui.episode
  "The views of the Episode page: the episode, the button that plays it, and
  its show notes, chapters and transcript, whose times play it from there.

  The page comes from the feed of its show at hand, as
  dk.simongray.podcast-app.episode describes it, so it shows at once when
  the start of the feed has the episode."
  (:require [dk.simongray.play-nice.media.fragment :as fragment]
            [dk.simongray.podcast.format.transcript :as transcript]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.text :as text]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.controls :as controls-ui]
            [dk.simongray.podcast-app.ui.details :as details]
            [dk.simongray.podcast-app.ui.facts :as facts-ui]
            [dk.simongray.podcast-app.ui.picks :as picks-ui]
            [dk.simongray.podcast-app.ui.show :as show-ui]
            [dk.simongray.podcast-app.ui.time :as time-ui]
            [dk.simongray.wary-fetch.url :as url]))

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
    (str "Play from " (time-ui/clock-text t))

    (and (number? position) (pos? position) (not played?))
    (if (number? duration)
      (str "Resume, " (time-ui/duration-text (max 0 (- duration position))) " left")
      "Resume")

    :else
    "Play"))

(defn play-button
  "The button that plays the episode `page`, from `t` seconds or from where
  its `entry` in the profile left it, or pauses it while the `player`
  plays it, in this tab or another."
  [page player entry t]
  (if (player/playing player (:id page))
    [:button.primary {:type "button" :on {:click [[:player/pause]]}} "Pause"]
    [:button.primary {:type "button" :on {:click [[:player/play (episode/playable page) t]]}}
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

(defn number-text
  "The number `n` of a season or an episode, without a fraction when it
  has none, e.g. 3 for 3.0."
  [n]
  (if (== n (Math/floor n))
    (str (long n))
    (str n)))

;; podcast namespace, season and episode: a season's name may stand in for
;; its number, and an episode's display text should. Apple puts the season
;; and the number above the title, as in "Season 17 · Episode 1 · Explicit".
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/season.md
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/episode.md
(defn numbering-facts
  "The season and the number of an episode by its `numbering`, as
  resolve/numbering gives it, and whether it's a trailer or a bonus."
  [{:keys [type season season-name episode episode-display]}]
  [(or season-name (some->> season number-text (str "Season ")))
   (or episode-display (some->> episode number-text (str "Episode ")))
   (case type "trailer" "Trailer" "bonus" "Bonus" nil)])

;; HTML, header and hgroup: the introduction of the episode's article, its
;; heading first, with its show as its subtitle
;; https://html.spec.whatwg.org/multipage/sections.html#the-header-element
;; https://html.spec.whatwg.org/multipage/sections.html#the-hgroup-element
(defn intro
  "The header of the article of the episode `page` of the Episode page of
  the `state`: its `heading` with its show, its artwork and facts, whether
  its entry in the profile is played, the button that plays it, from the
  start time of the link, and the buttons that queue it."
  [state heading {:keys [id show language numbering published duration explicit? ai-content?]
                  :as   page}]
  (let [entry (player/entry (:library state) id)]
    [:header.episode-intro
     [:hgroup heading [:p [:a {:href (:href show) :lang (text/lang language)} (:title show)]]]
     (artwork/img page 256 (merge {:fetchpriority "high" :sizes artwork/intro-sizes}
                                  (colors/placeholder state page)))
     [:div.episode-intro-body
      (facts-ui/line
       (concat (numbering-facts numbering)
               [(some-> published time-ui/date)
                (some-> duration time-ui/duration)
                (when explicit? "Explicit")
                (when ai-content? "Made with AI")
                (when (:played? entry) "Played")]))
      [:div.episode-actions
       (play-button page (:player state) entry (start-time state))
       (queue-buttons page (queue/place state id))]]]))

;; The Details of an episode hold what's its own rather than its show's,
;; since the show's are on the show's page
(defn details-rows
  "The rows of the Details of the episode `page`, with the links that
  support its show."
  [{:keys [link content-links funding locations license enclosure]}]
  (let [length (:length enclosure)]
    (concat
     [["Web page" [(details/web-link link nil)]]
      ["Also on" (map #(details/web-link (:href %) (:text %)) content-links)]
      ["Support" (details/support funding)]]
     (details/location-rows locations)
     [["License" [(details/license license)]]
      ;; TODO: the format and bitrate of the media file, and its size where
      ;; the feed gives none or a wrong one, from what read-files! reads of
      ;; it. They matter once episodes can be downloaded.
      ["File" [(when (and (number? length) (pos? length))
                 (text/size-text length))]]])))

;; A chapters file is the publisher's, so a chapter's link is only one when
;; it goes to the web, never e.g. a javascript: URL. Chapters name no
;; language of their own, so the titles take the feed's, while the times
;; stay in the page's (WCAG 2.2, SC 3.1.2 Language of Parts, technique H58).
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/examples/chapters/jsonChapters.md
;; https://www.w3.org/WAI/WCAG22/Understanding/language-of-parts.html
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H58
;; Folded, as the transcript is, so that the notes come before a long list
(defn chapters
  "The `chapters` of the episode `page` in the `state` as a list of their
  times and titles, which link to the web page of a chapter that has one,
  once the listener opened them."
  [state {:keys [id language] :as page} chapters]
  (let [lang (text/lang language)]
    (controls-ui/folded state {:id "chapters" :heading "Chapters" :place [:chapters id]}
                        #(vector :ol.chapters {:role "list"}
                                 (for [{:keys [start-time title url]} chapters]
                                   [:li {:replicant/key start-time}
                                    (time-link page start-time (time-ui/clock start-time))
                                    " "
                                    (if (url/web-url? url)
                                      (links/new-tab [:a {:href url :lang lang} (or title url)])
                                      (when title
                                        [:span {:lang lang} title]))])))))

;; WAI, Transcribing Audio to Text: the transcript is on the page of the
;; media itself, in paragraphs that name who speaks. It's behind a
;; disclosure, since an hour's transcript is ten thousand words, and it's
;; read once the listener opens it.
;; https://www.w3.org/WAI/media/av/transcripts/
;;
;; The transcript that resolve picks can be in another language than the
;; feed. The podcast namespace's transcript tag names its language, and a
;; transcript without one is in the feed's. Its text takes that language
;; (WCAG 2.2, SC 3.1.2 Language of Parts, technique H58).
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/transcript.md
;; https://www.w3.org/WAI/WCAG22/Understanding/language-of-parts.html
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H58
(defn transcript
  "The transcript of the episode `page` in the `state`, with the `files`
  read so far, in paragraphs of a speaker's turn or about a minute, once
  the listener opened it, which reads it."
  [state {:keys [id view] :as page} files]
  (let [{:keys [transcript-status]}       files
        {:keys [document error language]} (:transcript files)]
    (controls-ui/folded
     state {:id "transcript" :heading "Transcript" :place [:transcript id]}
     #(cond
        ;; read as it mounts, so that an open transcript whose read the
        ;; state forgot is read again when its page shows once more
        (not= :read transcript-status)
        [:p {:role "status" :replicant/on-mount [[:episode/read-transcript id view]]}
         "Loading the transcript…"]

        (or error (and (= :read transcript-status) (empty? (:segments document))))
        [:p {:role "status"} "The transcript couldn't be read."]

        document
        [:div.transcript {:lang (text/lang (or language (:language page)))}
         (for [{:keys [start-time speaker body]} (:segments (transcript/paragraphs document))]
           [:p
            (when start-time
              (list (time-link page start-time (time-ui/clock start-time)) " "))
            (when speaker
              (list [:strong speaker] ": "))
            body])]))))

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
      :looking-up show-ui/looking-up
      :loading    [:p {:role "status"} "Loading the episode…"]
      :no-show    show-ui/not-found
      :failed     [:p {:role "status"}
                   (show-ui/status-text :failed 0 (:error (show-ui/source state)) nil)]
      (list not-found
            [:p [:a {:href (routes/show-href guid nil (get-in state [:route :query-params :url]))}
                 "Go to the show"]
             " for its other episodes."]))))

(def picks-section
  "The section of the picks under an episode: its heading's id and text,
  the lead of its line of why, and what it says without picks."
  {:id "episode-picks" :heading "More like this" :lead "About"
   :none "Nothing in your library is like it yet."})

;; YouTube's Up next starts from the video that plays, and Nielsen Norman
;; Group found people expect recommendations after they've watched
;; something, as well as on a home page
;; https://support.google.com/youtube/answer/16089387
;; https://www.nngroup.com/articles/recommendation-expectations/
(defn content
  "The content of the Episode page of the `state` under its `heading`: an
  article, whose header has the heading, with the picks like it after its
  Details, or else the heading and what became of the episode."
  [state heading]
  (if-let [{:keys [id notes view language] :as page} (route-page state)]
    (let [files (get-in state [:episodes id])
          shown (episode/shown-chapters page files)
          aside (details/aside (details-rows page) (:people page))]
      (list
       ;; the files are read once the page of the episode is in the
       ;; document, and again for the page of another one
       (details/article
        {:replicant/key      (pr-str id)
         :replicant/on-mount [[:episode/read-files id view]]}
        [(intro state heading page)
         ;; the heading only for a screen reader, since the notes show
         ;; what they are
         (when (seq notes)
           [:section {:aria-labelledby "notes"}
            [:h2#notes.visually-hidden "Show notes"]
            [:div.show-notes {:lang (text/lang language)} notes]])
         (when shown
           (chapters state page shown))
         (when (:transcript view)
           (transcript state page files))]
        aside
        (picks-ui/folded state picks-section [:episode id] #(picks/like-episode state page)))))
    (list heading (missing state))))
