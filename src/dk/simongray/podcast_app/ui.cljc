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
            [dk.simongray.podcast-app.settings :as settings]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.text :as text]
            [dk.simongray.podcast-app.ui.episode :as episode-ui]
            [dk.simongray.podcast-app.ui.home :as home-ui]
            [dk.simongray.podcast-app.ui.inbox :as inbox-ui]
            [dk.simongray.podcast-app.ui.library :as library-ui]
            [dk.simongray.podcast-app.ui.player :as player-ui]
            [dk.simongray.podcast-app.ui.queue :as queue-ui]
            [dk.simongray.podcast-app.ui.search :as search]
            [dk.simongray.podcast-app.ui.settings :as settings-ui]
            [dk.simongray.podcast-app.ui.show :as show-ui]))

(def app-name
  "The name of the app, as Home's heading and the window's title show it."
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
;; is a part of the Library, whose page has the start of it
;; https://m3.material.io/components/navigation-bar/guidelines
(def destinations
  "The names of the routes that the main navigation links to, in order,
  Home first and Settings last."
  [:route/home :route/queue :route/library :route/search :route/settings])

(def part-of
  "The page of the navigation that each page outside it is a part of, by
  the names of their routes."
  {:route/inbox :route/library})

(defn page-name
  "The name of the page of the navigation that the route named
  `route-name` is, e.g. \"queue\", whose colour look.css has, or nil for a
  page that has no colour: Home, whose label is in the ink, and a page
  that the navigation doesn't link to."
  [route-name]
  (when (and (some #{route-name} destinations) (not= :route/home route-name))
    (name route-name)))

(defn page-title
  "The title of the page of the `state`: the app's name on Home, a show's
  own on its page, and an episode's on its."
  [state]
  (case (get-in state [:route :name])
    :route/home    app-name
    :route/show    (or (:title (show/page state (show-ui/guid state))) "Show")
    :route/episode (or (:title (episode-ui/route-page state)) "Episode")
    (get titles (get-in state [:route :name]) "Page not found")))

(defn title-lang
  "The lang attribute of the title of the page of the `state`: the language
  of the feed of a show or an episode that has a title, as text/lang has
  it, or nil."
  [state]
  (let [{:keys [title language]} (case (get-in state [:route :name])
                                   :route/show    (show/page state (show-ui/guid state))
                                   :route/episode (episode-ui/route-page state)
                                   nil)]
    (when title
      (text/lang language))))

;; WCAG 2.2, SC 2.4.2 Page Titled: the page before the app, as in example 1
;; of technique G88, which lists "the most important identifying information
;; first", since that's what tells the tabs of the same app apart
(defn document-title
  "The title of the window or tab for the page of the `state`, the app's
  name alone on Home."
  [state]
  (if (= :route/home (get-in state [:route :name]))
    app-name
    (str (page-title state) " – " app-name)))

(defn canonical-path
  "The path that the Show page or the Episode page of the `state` should
  have, or nil before the title is known: with the slug of the title, and
  for a show that the library keeps, its key there and no URL of its feed,
  since the library knows the URL."
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
        (routes/episode-href guid item-guid title url (episode-ui/start-time state)))

      nil)))

;; WAI-ARIA 1.2, aria-current: "page" marks the link to the current page
;; within a set of links, and "true" the current item of a set, here the
;; page that the current page is a part of
(defn page-link
  "A link to the route named `route-name` with the `label`, which is marked
  as the current page when the `state` is on that route, or as current
  when it's on a page that is a part of the route's page."
  [state route-name label]
  (let [current (get-in state [:route :name])]
    [:a {:href         (routes/href route-name)
         :aria-current (cond
                         (= route-name current)           "page"
                         (= route-name (part-of current)) "true")}
     label]))

;; WCAG 2.2, SC 2.4.1 Bypass Blocks, technique G1: a link at the top of the
;; page to the main content. It moves the focus itself rather than follow
;; its fragment, which would add an entry to the history.
(def skip-link
  "The first thing a keyboard reaches: a link past the navigation, to the
  heading of the page."
  [:a.skip-link {:href "#main"
                 :on   {:click [[:event/prevent-default]
                                [:page/focus-heading]]}}
   "Skip to content"])

;; AntennaPod counts the inbox in its navigation, here on the Library's
;; link, whose page has the inbox. The count is part of the link's name,
;; e.g. "Library 3 new episodes", which begins with what the link shows
;; (WCAG 2.2, SC 2.5.3 Label in Name).
;; https://github.com/AntennaPod/AntennaPod/pull/7398
(defn nav-label
  "The label of the link of the main navigation to the route named
  `route-name` for the `state`: its title, with the count of the inbox on
  the Library's."
  [state route-name]
  (let [n (when (= :route/library route-name)
            (count (inbox/episodes state)))]
    (if (pos? (or n 0))
      (list (titles route-name) " " (str n)
            [:span.visually-hidden " " (text/plural n "new episode")])
      (titles route-name))))

;; WAI-ARIA APG, landmark regions: a label that doesn't repeat the role,
;; which a screen reader announces anyway, e.g. "Main navigation". The
;; list's role is explicit, since the CSS removes its markers (see layout.css).
;; Each page's link names its page for its colour, from the palette that
;; the listener chose, unless they chose none.
(defn main-nav
  "The navigation between the pages for the `state`: the main pages and
  Settings, as a tab bar on a phone and a rail on a wider screen."
  [state]
  (let [palette (settings/navigation-palette state)]
    [:nav {:aria-label "Main"}
     [:ul {:role "list"}
      (for [route-name destinations]
        [:li {:data-page (when-not (= :none palette) (page-name route-name))}
         (page-link state route-name (nav-label state route-name))])]]))

;; The heading takes the focus after each navigation, which the users who
;; tested the techniques of client-side routing found the best experience
;; (Marcy Sutton with Fable Tech Labs, 2019):
;; https://v2.gatsbyjs.com/blog/2019-07-11-user-testing-accessible-client-routing
(defn page-heading
  "The heading of the page of the `state`, in the language of its title,
  which can take the focus."
  [state]
  [:h1 {:tabindex -1 :lang (title-lang state)} (page-title state)])

(def not-found
  "The content of the page of a path that no page has."
  [:p "There's no page here. Go to "
   [:a {:href (routes/href :route/home)} "Home"]
   " instead."])

;; WCAG 2.2, SC 4.1.3 Status Messages: what happened out of sight, e.g. a
;; subscription that finished, which every page announces in the same place
(defn announcer
  "The status line that a screen reader reads the `announcement` from,
  which only it shows."
  [announcement]
  [:p.visually-hidden {:role "status"} announcement])

(def library-pages
  "The names of the routes of the pages that show nothing without the
  listener's library."
  #{:route/queue :route/inbox :route/library :route/settings})

(def no-library
  "What a page of library-pages says when the library couldn't be read."
  [:p "The library couldn't be read."])

(defn main-content
  "The main content of the page of the `state`, under its heading, which
  the page of a show or an episode puts in the header of its article."
  [state]
  (let [route   (get-in state [:route :name])
        heading (page-heading state)]
    [:main#main
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
         :route/search   (search/content state)
         :route/settings (settings-ui/content state)
         :route/show     (show-ui/content state heading)
         :route/episode  (episode-ui/content state heading)
         nil))
     (announcer (:announcement state))
     [:p.visually-hidden {:id links/note-id} "Opens in a new tab"]]))

(defn shell-attrs
  "The attributes of the shell of the page of the `state`: the palette of
  the navigation that the listener chose, and the seeds of artwork as
  colors/attrs has them."
  [state]
  (merge {:data-palette (name (settings/navigation-palette state))}
         (colors/attrs state)))

(defn page
  "The whole page for the `state` of the app.

  The landmarks come in the same order at every width, so that the order of
  the focus doesn't change with the layout: the navigation, the main
  content and the player. The paper of a page about a show takes the
  colours of the show's artwork, and the player those of what plays."
  [state]
  [:div.shell (shell-attrs state)
   skip-link
   (main-nav state)
   (main-content state)
   (player-ui/region state)])
