(ns dk.simongray.podcast-app.ui
  "The views of the app: pure functions from the state to Hiccup.

  An event handler is a vector of actions, i.e. data, which Replicant
  passes to the dispatch function that dk.simongray.podcast-app.main sets.
  The state holds the current page as :route, a map of its :name,
  :path-params and :query-params, which is nil for a path that no page
  has. It holds the listener's library from podcast-clj as :library, once
  it's restored, and the last thing to tell a screen reader as
  :announcement."
  (:require [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.inbox :as inbox]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [dk.simongray.podcast-app.ui.inbox :as inbox-ui]
            [dk.simongray.podcast-app.ui.library :as library-ui]
            [dk.simongray.podcast-app.ui.player :as player-ui]
            [dk.simongray.podcast-app.ui.queue :as queue-ui]
            [dk.simongray.podcast-app.ui.search :as search]
            [dk.simongray.podcast-app.ui.settings :as settings-ui]
            [dk.simongray.podcast-app.ui.show :as show-ui]))

(def app-name
  "The name of the app, as the header and the window's title show it."
  "Podcasts")

(def titles
  "The title of each page by the name of its route."
  {:route/queue    "Queue"
   :route/inbox    "Inbox"
   :route/library  "Library"
   :route/search   "Search"
   :route/settings "Settings"})

(def destinations
  "The names of the routes that the main navigation links to, in order."
  [:route/queue :route/inbox :route/library :route/search])

(defn page-title
  "The title of the page of the `state`: a show's own on its page, and an
  episode's on its."
  [state]
  (case (get-in state [:route :name])
    :route/show    (or (:title (show/page state (show-ui/guid state))) "Show")
    :route/episode (or (:title (episode-ui/route-page state)) "Episode")
    (get titles (get-in state [:route :name]) "Page not found")))

;; WCAG 2.2, SC 2.4.2 Page Titled: the page before the app, as in example 1
;; of technique G88, which lists "the most important identifying information
;; first", since that's what tells the tabs of the same app apart
(defn document-title
  "The title of the window or tab for the page of the `state`."
  [state]
  (str (page-title state) " – " app-name))

;; WAI-ARIA 1.2, aria-current: "page" marks the link to the current page
;; within a set of links
(defn page-link
  "A link to the route named `route-name` with the `label`, which is marked
  as the current page when the `state` is on that route."
  [state route-name label]
  [:a {:href         (routes/href route-name)
       :aria-current (when (= route-name (get-in state [:route :name]))
                       "page")}
   label])

;; WCAG 2.2, SC 2.4.1 Bypass Blocks, technique G1: a link at the top of the
;; page to the main content. It moves the focus itself rather than follow
;; its fragment, which would add an entry to the history.
(def skip-link
  "The first thing a keyboard reaches: a link past the header and the
  navigation, to the heading of the page."
  [:a.skip-link {:href "#main"
                 :on   {:click [[:event/prevent-default]
                                [:page/focus-heading]]}}
   "Skip to content"])

(defn banner
  "The header of every page for the `state`: the app's name, which links to
  the queue, and the link to the settings."
  [state]
  [:header
   [:a {:href (routes/href :route/queue)} app-name]
   (page-link state :route/settings (titles :route/settings))])

;; AntennaPod counts the inbox in its navigation. The count is part of the
;; link's name, e.g. "Inbox 3 new episodes", which begins with what the
;; link shows (WCAG 2.2, SC 2.5.3 Label in Name).
;; https://github.com/AntennaPod/AntennaPod/pull/7398
(defn nav-label
  "The label of the link of the main navigation to the route named
  `route-name` for the `state`: its title, with the count of the inbox."
  [state route-name]
  (let [n (when (= :route/inbox route-name)
            (count (inbox/episodes state)))]
    (if (pos? (or n 0))
      (list (titles route-name) " " (str n)
            [:span.visually-hidden (if (= 1 n) " new episode" " new episodes")])
      (titles route-name))))

;; WAI-ARIA APG, landmark regions: a label that doesn't repeat the role,
;; which a screen reader announces anyway, e.g. "Main navigation". The
;; list's role is explicit, since the CSS removes its markers (see app.css).
(defn main-nav
  "The navigation between the main pages for the `state`: a tab bar on a
  phone and a rail on a wider screen."
  [state]
  [:nav {:aria-label "Main"}
   [:ul {:role "list"}
    (for [route-name destinations]
      [:li (page-link state route-name (nav-label state route-name))])]])

;; The heading takes the focus after each navigation, which the users who
;; tested the techniques of client-side routing found the best experience
;; (Marcy Sutton with Fable Tech Labs, 2019):
;; https://v2.gatsbyjs.com/blog/2019-07-11-user-testing-accessible-client-routing
(defn page-heading
  "The heading of the page of the `state`, which can take the focus."
  [state]
  [:h1 {:tabindex -1} (page-title state)])

(def not-found
  "The content of the page of a path that no page has."
  [:p "There's no page here. Go to the "
   [:a {:href (routes/href :route/queue)} "queue"]
   " instead."])

;; WCAG 2.2, SC 4.1.3 Status Messages: what happened out of sight, e.g. a
;; subscription that finished, which every page announces in the same place
(defn announcer
  "The status line that a screen reader reads the `announcement` from,
  which only it shows."
  [announcement]
  [:p.visually-hidden {:role "status"} announcement])

(defn main-content
  "The main content of the page of the `state`."
  [state]
  [:main#main
   (page-heading state)
   (case (get-in state [:route :name])
     nil             not-found
     :route/queue    (queue-ui/content state)
     :route/inbox    (inbox-ui/content state)
     :route/library  (library-ui/content state)
     :route/search   (search/content state)
     :route/settings (settings-ui/content state)
     :route/show     (show-ui/content state)
     :route/episode  (episode-ui/content state)
     nil)
   (announcer (:announcement state))
   [:p.visually-hidden {:id links/note-id} "Opens in a new tab"]])

(defn page
  "The whole page for the `state` of the app.

  The landmarks come in the same order at every width, so that the order of
  the focus doesn't change with the layout: the header, the navigation, the
  main content and the player. The page takes the colours of the artwork
  of what it's about, or else of what plays, as colors/focus has it."
  [state]
  [:div.shell (colors/attrs state (colors/focus state))
   skip-link
   (banner state)
   (main-nav state)
   (main-content state)
   (player-ui/region state)])
