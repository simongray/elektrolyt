(ns dk.simongray.podcast-app.colors
  "The colours of artwork that the look takes: the player takes the seed
  of the artwork of what plays, or of the image of its chapter, and the
  paper's links, buttons and tint take the seed of the show of a Show or an
  Episode page. Without one, or when the listener prefers the app's own
  colours, both keep the app's own near-black, which look.css has, so the
  paper has neutral links and no tint.

  The :colors of the state keep each artwork's colours by its URL, as a
  map of the :seed, a hex colour from colour-coded's choose/seed, nil for
  artwork without a hue, and the :dominant colour, which an artwork shows
  while it loads, or of the :status :loading while they're worked out."
  (:require [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.settings :as settings]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.shows :as shows]
            [dk.simongray.podcast-app.ui.artwork :as artwork]))

(defn source
  "The URL to work out the colours of the artwork of `x` from: the smallest
  of its sizes, as artwork/sizes gives them, since the colours need few
  pixels, or else its :artwork."
  [{:keys [artwork] :as x}]
  (or (:href (first (sort-by :width (artwork/sizes x)))) artwork))

(defn entry
  "The colours of the artwork of `x` in the :colors of the `state`, or nil
  while they're unknown."
  [state x]
  (some->> (:artwork x) (get (:colors state))))

(defn seed
  "The seed of the colours of the artwork of `x` in the `state`, or nil
  while they're unknown or where it has no hue."
  [state x]
  (:seed (entry state x)))

(defn playing
  "What colours the bars while it plays in the player of the `state`: the
  image of the chapter at the current time, or else the playback, whose
  artwork the player shows, or nil."
  [{:keys [player] :as state}]
  (let [playback (player/shown-playback player)
        chapter  (player/chapter-at playback (:current-time (player/shown-media player)))]
    (if-let [image (:img chapter)]
      {:artwork image}
      playback)))

(defn page-show
  "The show of the Show or Episode page of the `state`, whose artwork
  colours its links and buttons, or nil on any other page."
  [state]
  (let [{:keys [name path-params]} (:route state)]
    (when (#{:route/show :route/episode} name)
      (show/page state (:guid path-params)))))

(defn focus
  "What the paper of the page of the `state` takes its colours from: the
  show of a Show or an Episode page, once its colours are known, or else
  nil, for the app's own colours, as when the listener prefers them."
  [state]
  (when (settings/artwork-colors? state)
    (let [page (page-show state)]
      (when (seed state page)
        page))))

(defn listed
  "The episodes or shows that the lists of the page of the `state` show
  with their artwork: Home's, the Queue's, the Inbox's or the Library's,
  which are the listener's own, so that their colours are worth working
  out ahead."
  [{:keys [library] :as state}]
  (when library
    (case (get-in state [:route :name])
      :route/home    (concat (:episodes (queue/view library))
                             (:episodes (picks/home state)))
      :route/queue   (concat (:episodes (queue/view library))
                             (:episodes (picks/after-queue state)))
      :route/inbox   (inbox/episodes state)
      :route/library (concat (inbox/episodes state) (shows/all-shows library))
      nil)))

(defn once-each
  "The maps `xs` with each :artwork once, the first of each, in order."
  [xs]
  (first (reduce (fn [[kept seen] x]
                   (if (seen (:artwork x))
                     [kept seen]
                     [(conj kept x) (conj seen (:artwork x))]))
                 [[] #{}]
                 xs)))

(defn wanted
  "The artworks whose colours the page of the `state` shows and that the
  :colors don't have yet, as maps of the :artwork and the :artwork-sizes,
  those that colour the page first, and each once, though e.g. what plays
  and the page's show share theirs."
  [state]
  (->> (concat [(playing state) (page-show state)] (listed state))
       (filter :artwork)
       (remove #(contains? (:colors state) (:artwork %)))
       (map #(select-keys % [:artwork :artwork-sizes]))
       (once-each)))

(defn attrs
  "The attributes of the shell of the page of the `state`: the seed of
  the focus of its paper as the --seed of its style, and the seed of what
  plays, which colours the player, as its --bar-seed, each while it's
  known, so that the page keeps the app's own colours until then."
  [state]
  (let [paper (some->> (focus state) (seed state))
        bars  (when (settings/artwork-colors? state) (seed state (playing state)))
        style (cond-> {}
                paper (assoc :--seed paper)
                bars  (assoc :--bar-seed bars))]
    (if (seq style) {:style style} {})))

;; Timelthaler, Dominant colors for lazy-loading images: Pinterest and
;; Google Images show an image's dominant colour in its place while it
;; loads, so that the page has its colours at once
;; https://manu.ninja/dominant-colors-for-lazy-loading-images/
(defn placeholder
  "The attributes of the img of the artwork of `x` in the `state`: its
  dominant colour behind it, while it loads, once that's known."
  [state x]
  (when-let [c (:dominant (entry state x))]
    {:style {:background-color c}}))

(comment
  (wanted {:route  {:name :route/queue}
           :player {:playback {:artwork "https://example.org/a.jpg"}}})
  ;; => [{:artwork "https://example.org/a.jpg"}]
  #_.)
