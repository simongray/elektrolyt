(ns dk.simongray.podcast-app.ui
  "The views of the app: pure functions from the state to Hiccup.

  An event handler is a vector of actions, i.e. data, which Replicant
  passes to the dispatch function that dk.simongray.podcast-app.main sets.
  The state holds the current page as :route, a map of its :name and
  :path-params, which is nil for a path that no page has."
  (:require [dk.simongray.podcast-app.routes :as routes]))

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
  "The title of the page of the `state`."
  [state]
  (get titles (get-in state [:route :name]) "Page not found"))

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
      [:li (page-link state route-name (titles route-name))])]])

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

(defn main-content
  "The main content of the page of the `state`."
  [state]
  [:main#main
   (page-heading state)
   (when-not (:route state)
     not-found)])

;; WAI-ARIA APG, landmark regions: a section with a label is a region, the
;; landmark for content that none of the others describes
(defn player
  "The player of the episode that plays, which stays as the pages change."
  [state]
  [:section {:aria-label "Player"}
   [:p "Nothing is playing."]])

(defn page
  "The whole page for the `state` of the app.

  The landmarks come in the same order at every width, so that the order of
  the focus doesn't change with the layout: the header, the navigation, the
  main content and the player."
  [state]
  [:div.shell
   skip-link
   (banner state)
   (main-nav state)
   (main-content state)
   (player state)])
