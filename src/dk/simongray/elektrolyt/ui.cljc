(ns dk.simongray.elektrolyt.ui
  "The page of the app as Hiccup, from the state: the window's title, the
  navigation, the main content and the player."
  (:require [dk.simongray.elektrolyt.episode.ui :as episode-ui]
            [dk.simongray.elektrolyt.library.inbox.ui :as inbox-ui]
            [dk.simongray.elektrolyt.library.queue.ui :as queue-ui]
            [dk.simongray.elektrolyt.library.saves.ui :as saves-ui]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.library.settings.ui :as settings-ui]
            [dk.simongray.elektrolyt.library.ui :as library-ui]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.player.ui :as player-ui]
            [dk.simongray.elektrolyt.search.ui :as search-ui]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.ui :as show-ui]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.home :as home-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.loading :as loading]
            [dk.simongray.elektrolyt.ui.text :as text]))

(def app-name
  "The name of the app."
  "Elektrolyt")

(def titles
  "The title of each page by the name of its route."
  {:route/home     "Home"
   :route/queue    "Queue"
   :route/inbox    "Inbox"
   :route/library  "Library"
   :route/search   "Search"
   :route/settings "Settings"})

;; Material 3's navigation bar has three to five destinations, so the Inbox
;; is part of the Library, whose page shows the start of it
;; https://m3.material.io/components/navigation-bar/guidelines
(def destinations
  "The names of the routes that the main navigation links to, in order."
  [:route/home :route/queue :route/library :route/search :route/settings])

(def part-of
  "The page in the navigation that each page outside it is part of, by
  route name."
  {:route/inbox :route/library})

(def nav-icons
  "The icon of each page in the main navigation, by route name."
  {:route/home     icons/house
   :route/queue    icons/queue
   :route/library  icons/books
   :route/search   icons/magnifying-glass
   :route/settings icons/gear-six})

(defn page-name
  "The name of the route `route-name`, e.g. \"queue\", or nil when the
  navigation doesn't link to it."
  [route-name]
  (when (some #{route-name} destinations)
    (name route-name)))

(defn page-title
  "The title of the page in `state`: the app's name on Home, and the title
  of the show or the episode on its page."
  [state]
  (case (get-in state [:route :name])
    :route/home    app-name
    :route/show    (or (:title (show/page state (show-ui/guid state)))
                       (some-> (:library state) (show/subscription-of (show-ui/guid state)) :title)
                       "Show")
    :route/episode (or (:title (episode-ui/route-page state)) "Episode")
    (get titles (get-in state [:route :name]) "Page not found")))

(defn title-lang
  "The lang attribute of the page's title in `state`: the feed's language
  on the page of a show or an episode with a title, or else nil."
  [state]
  (let [{:keys [title language]} (case (get-in state [:route :name])
                                   :route/show    (show/page state (show-ui/guid state))
                                   :route/episode (episode-ui/route-page state)
                                   nil)]
    (when title
      (text/lang language))))

;; WCAG 2.2, SC 2.4.2 Page Titled, technique G88, example 1: the page before
;; the app, the most identifying first, since the page tells the tabs apart
(defn document-title
  "The window's title for the page in `state`, which is the app's name
  alone on Home."
  [state]
  (if (= :route/home (get-in state [:route :name]))
    app-name
    (str (page-title state) " – " app-name)))

;; 32 pixels fill a tab's 16 CSS pixels at twice the density
(defn favicon
  "The URL of the page's icon in `state`: the artwork of what the player
  shows, playing or paused, or nil while it shows nothing."
  [state]
  (when-let [artwork (:artwork (player/shown-playback state))]
    (artwork/smallest {:artwork artwork} 32)))

(defn canonical-path
  "The canonical path of the Show or Episode page in `state`, with the slug
  of its title, or nil before the title is known. A show that the listener
  subscribes to goes by its key in the library, without its feed's URL."
  [{{:keys [name path-params query-params]} :route :as state}]
  (let [guid (:guid path-params)
        url  (:url query-params)]
    (case name
      :route/show
      (when-let [title (:title (show/page state guid))]
        (if-let [k (some-> (:library state) (show/library-key guid url))]
          (routes/show-href k title)
          (routes/show-href guid title url)))

      :route/episode
      (when-let [{:keys [guid item-guid title url]} (episode-ui/route-page state)]
        (routes/episode-href guid item-guid title url (episode-ui/link-time state)))

      nil)))

;; WAI-ARIA 1.2, aria-current: "page" marks the link to the current page
;; within a set of links, and "true" the current item of a set, here the
;; page that the current page is part of
(defn page-link
  "A link with the `label` to the route `route-name`, marked as current
  when the page in `state` is that route or part of it. Its tooltip is the
  page's title, for where the link shows its icon alone."
  [state route-name label]
  (let [current (get-in state [:route :name])]
    [:a {:href         (routes/href route-name)
         :title        (titles route-name)
         :aria-current (cond
                         (= route-name current)           "page"
                         (= route-name (part-of current)) "true")}
     label]))

;; WCAG 2.2, SC 2.4.1 Bypass Blocks, technique G1: a link at the top of the
;; page to the main content. It moves the focus itself, since following its
;; fragment would add an entry to the history.
(def skip-link
  "A link past the navigation to the page's heading, the first thing that a
  keyboard reaches."
  [:a.skip-link {:href "#main"
                 :on   {:click [[:event/prevent-default]
                                [:page/focus-heading]]}}
   "Skip to content"])

;; WAI-ARIA APG, landmark regions: the label leaves out the role, which a
;; screen reader announces anyway, e.g. "Main navigation". The list's role
;; is explicit, since the CSS removes its markers. The data-page of each
;; item, and of the nav on one of its pages, selects a colour from the
;; listener's palette. On a phone, a link shows its icon alone, as
;; Material's navigation bar can, and its title is for a screen reader.
;; https://github.com/material-components/material-components-android/blob/master/docs/components/BottomNavigation.md
(defn main-nav
  "The main navigation for `state`, a tab bar on a phone and a rail on a
  wider screen."
  [state]
  (let [palette (settings/navigation-palette state)
        colour  #(when-not (= :none palette) (page-name %))
        current (get-in state [:route :name])]
    [:nav {:aria-label "Main" :data-page (colour (get part-of current current))}
     [:ul {:role "list"}
      (for [route-name destinations]
        [:li {:data-page (colour route-name)}
         (page-link state route-name
                    (list [:span.title (titles route-name)]
                          [controls/icon {:icon (nav-icons route-name)}]))])]]))

;; The heading takes the focus after each navigation, which users found best
;; in Marcy Sutton's tests of client-side routing with Fable Tech Labs, 2019
;; https://v2.gatsbyjs.com/blog/2019-07-11-user-testing-accessible-client-routing
(defn page-heading
  "The heading of the page in `state`, in its title's language, which can
  take the focus."
  [state]
  ;; data-refocus, since a Show or Episode page moves its heading into its
  ;; article once the feed arrives
  [:h1 {:tabindex -1 :lang (title-lang state) :data-refocus "main h1"} (page-title state)])

(def not-found
  "The content for a path that no page has."
  [:p "There's no page here. Go to "
   [:a {:href (routes/href :route/home)} "Home"]
   " instead."])

;; WCAG 2.2, SC 4.1.3 Status Messages: one place on every page announces
;; what happened out of sight, e.g. a finished subscription
(defn announcer
  "A status line of the announcement in `state` for a screen reader,
  hidden from sight. Each message is a new element by its count, so that
  one that repeats the last is read out too."
  [{:keys [announcement announcements]}]
  [:p.visually-hidden {:role "status"}
   [:span {:replicant/key announcements} announcement]])

;; NN/g: a wait of less than a second needs no sign, so the skeleton shows
;; only after one (look.css)
;; https://www.nngroup.com/articles/progress-indicators/
(def skeleton
  "The content of a page while the app starts: a line for a screen reader,
  and blocks in the place of the content, hidden from it."
  (list [:p.visually-hidden "Loading…"] loading/lines))

(def library-pages
  "The names of the routes whose pages need the listener's library."
  #{:route/queue :route/inbox :route/library :route/settings})

(def no-library
  "The content of a page that needs the library when it couldn't be read,
  which a reload may read."
  (notices/line {:label "Library not read"}
                "The library couldn't be read."
                [controls/button {:icon icons/arrows-clockwise :on {:click [[:page/reload]]}}
                 "Reload" [:span.visually-hidden " the page"]]))

;; While an episode plays, the store changes every second for the player's
;; clock alone. Replicant compares the Hiccup of an element with = before
;; it looks inside, so main as it was costs it almost nothing.
;; https://replicant.fun/top-down/
(def main-content
  "The main content of the page in `state`, under its heading, which a
  Show or Episode page puts in its article instead, or the heading over
  the skeleton while the app starts. It's made again only when the state
  changes beyond the player's clock, as the content-facts of the player
  tell."
  (memo/remember-last
   (fn [state]
     (let [route   (get-in state [:route :name])
           heading (page-heading state)]
       (if (:starting? state)
         [:main#main {:aria-busy "true"} [:div.paper heading skeleton]]
         [:main#main
          [:div.paper
           (saves-ui/notice state)
           (when-not (#{:route/show :route/episode} route)
             heading)
           (if (and (library-pages route) (nil? (:library state)))
             no-library
             (case route
               nil             not-found
               :route/home     (home-ui/content state)
               :route/queue    (queue-ui/content state)
               :route/inbox    (inbox-ui/content state)
               :route/library  (library-ui/content state)
               :route/search   (search-ui/content state)
               :route/settings (settings-ui/content state)
               :route/show     (show-ui/content state heading)
               :route/episode  (episode-ui/content state heading)
               nil))]
          (announcer state)
          (for [{:keys [id text]} (vals links/notes)]
            [:p.visually-hidden {:id id} text])])))
   (fn [{:keys [player] :as state}]
     [(dissoc state :player) (player/content-facts player)])
   (fn [[state facts] [last-state last-facts]]
     (and (= facts last-facts) (memo/same-entries? state last-state)))))

(defn shell-attrs
  "The attributes of the page's shell in `state`: the navigation palette
  that the listener chose, and the seeds of artwork."
  [state]
  (merge {:data-palette (name (settings/navigation-palette state))}
         (colors/attrs state)))

;; The shell of the frame at the start is another element than the page's,
;; so that the page opens in the colours of its artwork rather than turns
;; to them from the app's own
(defn page
  "The whole page for `state`, with the navigation, the main content and
  the player in the same order at every width, so that the focus order
  doesn't change with the layout."
  [state]
  [:div.shell (cond-> (shell-attrs state)
                (:starting? state) (assoc :replicant/key :starting))
   skip-link
   (main-nav state)
   (main-content state)
   (player-ui/region state)])

(defn starting
  "The `state` of the page's frame at `path` while the app starts, before
  the library is read and the router runs: the navigation, and the page's
  heading over the skeleton."
  [state path]
  (assoc state :route (routes/route-at path) :starting? true))
