(ns dk.simongray.elektrolyt.portfolio
  "The Portfolio workbench of the views, with the app's stylesheet, in the
  sizes of a desktop, a phone, and the 320 CSS pixels that content must
  reflow to (WCAG 2.2, SC 1.4.10), and as each colour vision deficiency
  sees them."
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded.vision :as vision]
            [phosphor.icons :as phosphor]
            [portfolio.ui :as portfolio]
            [portfolio.ui.canvas.addons :as addons]
            [portfolio.ui.components.canvas :as canvas]
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

(defn matrix-values
  "The values of an SVG feColorMatrix that applies the `matrix`, three
  rows of red, green and blue, and leaves the alpha as it is."
  [matrix]
  (str/join " " (concat (mapcat #(concat % [0 0]) matrix) [0 0 0 1 0])))

(defn vision-filter
  "The id of the SVG filter that simulates the `deficiency`."
  [deficiency]
  (str "vision-" (name deficiency)))

;; Machado, Oliveira and Fernandes (2009) give a matrix for each colour
;; vision deficiency, which applies to linear light. That's the colour
;; space in which an SVG filter works unless it says otherwise, so an
;; feColorMatrix with the matrix of colour-coded's vision namespace shows
;; a scene as a dichromat sees it.
;; https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html
;; https://drafts.fxtf.org/filter-effects/#feColorMatrixElement
(defn vision-filters!
  "Add the SVG filters of the colour vision deficiencies to the
  workbench's document, unless they're there."
  []
  (when-not (js/document.getElementById "vision-filters")
    (let [svg (js/document.createElementNS "http://www.w3.org/2000/svg" "svg")]
      (doto svg
        (.setAttribute "id" "vision-filters")
        (.setAttribute "aria-hidden" "true")
        (.setAttribute "width" "0")
        (.setAttribute "height" "0")
        (.setAttribute "style" "position: absolute"))
      (set! (.-innerHTML svg)
            (str/join (for [d vision/deficiencies]
                        (str "<filter id=\"" (vision-filter d) "\" color-interpolation-filters=\"linearRGB\">"
                             "<feColorMatrix type=\"matrix\" values=\"" (matrix-values (vision/matrix d 1.0)) "\"/>"
                             "</filter>"))))
      (.append js/document.body svg))))

(defn filtered!
  "Show the scene of the canvas `el` through the filter of the
  :vision/deficiency of the tools' values, or as it is without one."
  [_ el {:vision/keys [deficiency]}]
  (set! (.. (canvas/get-iframe el) -style -filter)
        (if deficiency
          (str "url(#" (vision-filter deficiency) ")")
          "")))

(def vision-tool
  "A menu of the workbench's toolbar that shows a scene as someone with a
  colour vision deficiency sees it."
  (addons/create-toolbar-menu-button
   {:id             :canvas/colour-vision
    :title          "Colour vision"
    ;; Portfolio's own icons, since its toolbar draws them
    :icon           (phosphor/icon :phosphor.regular/eye)
    :options        [{:title "Typical" :value {:vision/deficiency nil}}
                     {:title "Protan, red-blind" :value {:vision/deficiency :protan}}
                     {:title "Deutan, green-blind" :value {:vision/deficiency :deutan}}
                     {:title "Tritan, blue-blind" :value {:vision/deficiency :tritan}}]
    :default-value  {:vision/deficiency nil}
    :prepare-canvas filtered!}))

(defn start
  "Start the workbench, or render it again after a reload."
  []
  (r/set-dispatch! log-actions)
  (vision-filters!)
  (portfolio/start!
   {:config             {:canvas-path       "/canvas.html"
                         :css-paths         ["/css/app.css"]
                         :viewport/defaults (:value (first viewports))
                         :viewport/options  viewports}
    :extra-canvas-tools [vision-tool]}))

(start)
