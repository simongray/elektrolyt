(ns dk.simongray.podcast-app.portfolio
  "The workbench of the views, at http://localhost:8402 while shadow-cljs
  watches the portfolio build.

  The scenes come from the namespaces of dev/ that end in -scenes. Each
  renders with the app's stylesheet, in the width of a desktop, a phone, or
  the 320 CSS pixels that WCAG 2.2 asks content to reflow to (SC 1.4.10)."
  (:require [portfolio.ui :as portfolio]
            [replicant.dom :as r]))

(defn log-actions
  "Log the `actions` of an event handler in a scene, which has no app to
  dispatch them to, with the `dispatch-data` of the event."
  [dispatch-data actions]
  (js/console.log "Actions:" (pr-str actions) dispatch-data))

(defn- viewport
  "The viewport option of the `title`, `width` by `height` CSS pixels.

  It has a height and no padding, since the page fills the window, which a
  scene that Portfolio fits to its content doesn't have."
  [title width height]
  {:title title
   :value {:viewport/width   width
           :viewport/height  height
           :viewport/padding [0 0 0 0]}})

(def viewports
  "The sizes that each scene can render in: a desktop, a phone, and the
  width of the reflow criterion."
  [(viewport "Desktop, 1280 × 800" 1280 800)
   (viewport "Phone, 390 × 844" 390 844)
   (viewport "Reflow, 320 × 640" 320 640)])

(defn start
  "Start the workbench, or render it again after a reload."
  []
  (r/set-dispatch! log-actions)
  (portfolio/start!
   {:config {:canvas-path       "/canvas.html"
             :css-paths         ["/css/app.css"]
             :viewport/defaults (:value (first viewports))
             :viewport/options  viewports}}))

(start)
