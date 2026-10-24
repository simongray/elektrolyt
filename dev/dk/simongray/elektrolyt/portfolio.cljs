(ns dk.simongray.elektrolyt.portfolio
  "The Portfolio workbench of the views, with the app's stylesheet, in the
  sizes of a desktop, a phone, and the 320 CSS pixels that content must
  reflow to (WCAG 2.2, SC 1.4.10)."
  (:require [portfolio.ui :as portfolio]
            [replicant.dom :as r]))

(defn log-actions
  "Log the `actions` of an event handler with the event's `dispatch-data`,
  since a scene has no app to dispatch them to."
  [dispatch-data actions]
  (js/console.log "Actions:" (pr-str actions) dispatch-data))

(defn- viewport
  "The viewport option `title`, of `width` by `height` CSS pixels. It has a
  height and no padding, since the app's page fills the window, unlike a
  scene that Portfolio fits to its content."
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
