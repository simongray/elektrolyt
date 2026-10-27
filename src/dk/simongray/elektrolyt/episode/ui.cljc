(ns dk.simongray.elektrolyt.episode.ui
  "The views of the Episode page: the episode, its buttons, and its show
  notes, chapters and transcript, whose times play it from there."
  (:require [dk.simongray.play-nice.media.fragment :as fragment]
            [dk.simongray.podcast.format.transcript :as transcript]
            [dk.simongray.elektrolyt.episode :as episode]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.picks.ui :as picks-ui]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.show.ui :as show-ui]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.details :as details]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.loading :as loading]
            [dk.simongray.elektrolyt.ui.text :as text]
            [dk.simongray.elektrolyt.ui.time :as time-ui]
            [dk.simongray.wary-fetch.url :as url]))

;; MDN: a link is for a real URL. Each time links to the page at ?t=, which
;; can be copied or opened in a tab of its own, and a click plays the
;; episode from there instead, like a time in a YouTube video's
;; description.
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
  "The page of the episode that the route in `state` names, or nil."
  [state]
  (let [{:keys [path-params slug]} (:route state)]
    (episode/page state (:guid path-params) (:hash path-params) slug time-link)))

(defn link-time
  "The seconds that the t of the link in `state` starts the episode at,
  e.g. 73 for ?t=73 or ?t=1m13s, or nil."
  [state]
  (some->> (get-in state [:route :query-params :t])
           (str "?t=")
           (fragment/link-time)))

(defn started?
  "Whether an episode's `entry` in the profile is started and not yet
  played."
  [{:keys [position played?]}]
  (and (number? position) (pos? position) (not played?)))

;; The time left is among the episode's facts, like in the rows of the
;; queue and the inbox, rather than in Resume, which it made wide
(defn time-left
  "How long an episode of `duration` seconds lasts, or has left once its
  `entry` in the profile is started, e.g. 50m left, or nil when the
  duration is unknown."
  [duration {:keys [position] :as entry}]
  (when (number? duration)
    (if (started? entry)
      (conj (time-ui/duration (max 0 (- duration position))) " left")
      (time-ui/duration duration))))

;; WAI-ARIA APG, button pattern: a button whose label changes from Play to
;; Pause has no aria-pressed, which would mean the opposite
;; https://www.w3.org/WAI/ARIA/apg/patterns/button/
(defn play-label
  "The label of the button that plays an episode, by its `entry` in the
  profile and the link's start time `t`: \"Play from 1:13\", \"Resume\" or
  \"Play\"."
  [entry t]
  (cond
    t                (str "Play from " (time-ui/clock-text t))
    (started? entry) "Resume"
    :else            "Play"))

(defn play-button
  "The button that plays the episode `page` from `t` seconds or from where
  its `entry` in the profile left it, or pauses it while the `player`
  plays it, in this tab or another. While it waits for data, Pause is
  marked data-waiting, like the player's."
  [page player entry t]
  (if (player/playing player (:id page))
    [controls-ui/button {:icon         icons/pause
                         :class        "primary"
                         :data-waiting (player/waiting? player (:id page))
                         :on           {:click [[:player/pause]]}}
     "Pause"]
    [controls-ui/button {:icon  icons/play
                         :class "primary"
                         :on    {:click [[:player/play (episode/playable page) t]]}}
     (play-label entry t)]))

;; Apple, Pocket Casts, Castro and Overcast queue an episode in two places,
;; right after the one that plays or at the end, which Apple Podcasts named
;; Play Next and Play Last. Play next moves an episode that's queued
;; already. Remove has the minus of Unsubscribe, and a double click on Play
;; last doesn't reach it.
;; https://support.apple.com/en-ca/guide/podcasts/use-up-next-poddfb50a470/1.1.0/mac/13.0
(defn queue-buttons
  "The buttons that queue the episode `page` by its place in the queue,
  `queued`: nil, :queued, or :current, which needs none."
  [{:keys [title] :as page} queued]
  (let [playable (episode/playable page)]
    (when-not (= :current queued)
      (list
       [controls-ui/button {:icon icons/rows-plus-top :on {:click [[:queue/add playable title true]]}}
        "Play next"]
       (if (= :queued queued)
         [controls-ui/button {:icon icons/minus
                              :on   {:click (controls-ui/single-click [[:queue/remove (:id page)]])}}
          "Remove from queue"]
         [controls-ui/button {:icon icons/rows-plus-bottom
                              :on   {:click (controls-ui/single-click [[:queue/add playable title false]])}}
          "Play last"])))))

(defn number-text
  "The number `n` of a season or an episode as text, e.g. 3 for 3.0."
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
  "The season and number of an episode by its `numbering`, and whether
  it's a trailer or a bonus."
  [{:keys [type season season-name episode episode-display]}]
  [(or season-name (some->> season number-text (str "Season ")))
   (or episode-display (some->> episode number-text (str "Episode ")))
   (case type "trailer" "Trailer" "bonus" "Bonus" nil)])

;; HTML, header and hgroup: the introduction of the episode's article, its
;; heading first, with its show as its subtitle
;; https://html.spec.whatwg.org/multipage/sections.html#the-header-element
;; https://html.spec.whatwg.org/multipage/sections.html#the-hgroup-element
(defn intro
  "The header of the article of the episode `page` in `state`: the
  `heading` with its show, the artwork, the facts, and the buttons that
  play and queue it."
  [state heading {:keys [id show language numbering published explicit? ai-content? supplement]
                  :as   page}]
  (let [entry    (player/entry (:library state) id)
        ;; as the player has it: what it measured, then the files, then the feed
        duration (or (:duration entry) (get-in state [:episodes id :result :duration]) (:duration page))]
    [:header.episode-intro
     [:hgroup heading [:p [:a {:href (:href show) :lang (text/lang language)} (:title show)]]]
     (artwork/img page 256 (merge {:fetchpriority "high" :sizes artwork/intro-sizes :under true}
                                  (colors/placeholder state page)))
     [:div.episode-intro-body
      (facts-ui/line
       (concat (numbering-facts numbering)
               [(some-> published time-ui/date)
                (time-left duration entry)
                (when explicit? "Explicit")
                (when ai-content? "Made with AI")
                (facts-ui/labelled-source supplement)
                (when (:played? entry) "Played")]))
      [:div.episode-actions
       (play-button page (:player state) entry (player/start-time duration (link-time state)))
       (queue-buttons page (queue/place state id))]]]))

(defn source
  "The site of the feed `supplement` that an episode came from, as a link
  for the Details, or nil when the episode is its show's own."
  [supplement]
  (when-let [host (facts-ui/source-host supplement)]
    (details/web-link (str "https://" host "/") host)))

;; The Details of an episode hold what's its own rather than its show's,
;; since the show's are on the show's page
(defn details-rows
  "The rows of the Details of the episode `page`, with the links that
  support it or its show."
  [{:keys [link content-links funding locations license enclosure supplement]}]
  (let [length (:length enclosure)]
    (concat
     [["Web page" [(details/web-link link nil)]]
      ["Source" [(source supplement)]]
      ["Also on" (map #(details/web-link (:href %) (:text %)) content-links)]
      ["Support" (details/support funding)]]
     (details/location-rows locations)
     [["License" [(details/license license)]]
      ;; TODO: the media file's format and bitrate, and its size where the
      ;; feed's is missing or wrong, from what's read of the file. They
      ;; matter once episodes can be downloaded.
      ["File" [(when (and (number? length) (pos? length))
                 (text/size-text length))]]])))

;; A chapters file is the publisher's, so a chapter links only to the web,
;; never e.g. to a javascript: URL. Chapters name no language of their own,
;; so the titles take the feed's while the times stay in the page's (WCAG
;; 2.2, SC 3.1.2 Language of Parts, technique H58).
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/examples/chapters/jsonChapters.md
;; https://www.w3.org/WAI/WCAG22/Understanding/language-of-parts.html
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H58
;; The same list while they stay, so that Replicant skips it, since the
;; page renders at every change of the state
(def chapter-list
  "The list of the `chapters` of the episode `page`, with their titles in
  the language `lang`."
  (memo/remember-last
   (fn [chapters page lang]
     [:ol.chapters {:role "list"}
      (for [{:keys [start-time title url]} chapters]
        [:li {:replicant/key start-time}
         (time-link page start-time (time-ui/clock start-time))
         " "
         (if (url/web-url? url)
           (links/new-tab [:a {:href url :lang lang} (or title url)])
           (when title
             [:span {:lang lang} title]))])])))

(defn chapters
  "The `chapters` of the episode `page` in `state`, folded like the
  transcript so that the notes come before a long list. Each has its time
  and its title, which links to the chapter's web page, if any."
  [state {:keys [id language] :as page} chapters]
  (controls-ui/folded state {:id "chapters" :heading "Chapters" :place [:chapters id]}
                      #(chapter-list chapters page (text/lang language))))

;; WAI, Transcribing Audio to Text: the transcript is on the media's own
;; page, in paragraphs that name who speaks. It's folded, since an hour's
;; transcript is ten thousand words.
;; https://www.w3.org/WAI/media/av/transcripts/
;;
;; A transcript can be in another language than the feed. The podcast
;; namespace's transcript tag names its language, or else it's the feed's,
;; and the text is marked with it (WCAG 2.2, SC 3.1.2 Language of Parts,
;; technique H58).
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/tags/transcript.md
;; https://www.w3.org/WAI/WCAG22/Understanding/language-of-parts.html
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H58
;; An hour's transcript of 210 paragraphs took 14 ms to make at each render
(def transcript-text
  "The transcript `document` of the episode `page` in paragraphs of a
  speaker's turn or about a minute, in the language `lang`, the same while
  they stay."
  (memo/remember-last
   (fn [document page lang]
     [:div.transcript {:lang lang}
      (for [{:keys [start-time speaker body]} (:segments (transcript/paragraphs document))]
        [:p
         (when start-time
           (list (time-link page start-time (time-ui/clock start-time)) " "))
         (when speaker
           (list [:strong speaker] ": "))
         body])])))

(defn transcript
  "The transcript of the episode `page` in `state` from the `files` read so
  far, in paragraphs of a speaker's turn or about a minute. It's read once
  the listener opens it."
  [state {:keys [id view] :as page} files]
  (let [{:keys [transcript-status]}       files
        {:keys [document error language]} (:transcript files)]
    (controls-ui/folded
     state {:id "transcript" :heading "Transcript" :place [:transcript id]}
     #(cond
        ;; read on mount, so that an open transcript whose read was dropped
        ;; from the state is read again when its page shows once more
        (not= :read transcript-status)
        (list (loading/status {:replicant/on-mount [[:episode/read-transcript id view]]}
                               "Loading the transcript…")
              loading/lines)

        (or error (empty? (:segments document)))
        [:p {:role "status"} "The transcript couldn't be read."]

        document
        (transcript-text document page (text/lang (or language (:language page))))))))

(def not-found
  "The content of an Episode page whose episode isn't in its show's feed."
  [:p "This episode isn't in its show's feed any more, "
   "or the link to it is out of date."])

(defn missing
  "The content of the Episode page in `state` while it has no episode to
  show: that its show or feed is on the way, why it failed, or that the
  episode isn't in the feed and a link to the show."
  [state]
  (let [{:keys [guid]} (get-in state [:route :path-params])]
    (case (episode/status state guid)
      ;; lines in the place of the notes, as the page's skeleton has them
      :looking-up (list show-ui/looking-up loading/lines)
      :loading    (list (loading/status "Loading the episode…") loading/lines)
      :no-show    show-ui/not-found
      :failed     [:p {:role "status"}
                   (show-ui/status-text :failed 0 (:error (show-ui/source state)))]
      (list not-found
            [:p [:a {:href (routes/show-href guid nil (get-in state [:route :query-params :url]))}
                 "Go to the show"]
             " for its other episodes."]))))

(def picks-section
  "The section of the picks under an episode: the id and text of its
  heading, the word before the terms that explain the picks, and the text
  without picks."
  {:id "episode-picks" :heading "More like this" :lead "About"
   :none "Nothing in your library is like it yet."})

;; YouTube's Up next starts from the video that plays, and Nielsen Norman
;; Group found people expect recommendations after they've watched
;; something, as well as on a home page
;; https://support.google.com/youtube/answer/16089387
;; https://www.nngroup.com/articles/recommendation-expectations/
(defn content
  "The content of the Episode page in `state` with its `heading`: the
  episode's article and the picks like it, or else the heading and what
  became of the episode."
  [state heading]
  (if-let [{:keys [id notes view language] :as page} (route-page state)]
    (let [files (get-in state [:episodes id])
          shown (episode/shown-chapters page files)
          aside (details/aside (details-rows page) (:people page))]
      (list
       ;; the files are read on mount, and the key mounts the article again
       ;; for another episode
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
        (let [place [:episode id (:guid page)]]
          (picks-ui/folded state picks-section place #(picks/fresh state place))))))
    (list heading (missing state))))
