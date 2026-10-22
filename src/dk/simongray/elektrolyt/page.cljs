(ns dk.simongray.elektrolyt.page
  "What the app does to the document itself: renders the page into it,
  gives the window its title, address and colours, and moves the focus
  and the scroll."
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.elektrolyt.local :as local]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.ui :as ui]
            [replicant.dom :as r]))

;; The app's former name, since a browser keeps the listener's data under
;; them
(def theme-key
  "The key in localStorage of a copy of the colours that the listener
  chose, which public/theme.js applies before the page first renders,
  rather than show the device's until the library is restored."
  "dk.simongray.podcast-app.theme")

(def contrast-key
  "The key in localStorage of a copy of the contrast that the listener
  chose, which public/theme.js applies as it does the colours."
  "dk.simongray.podcast-app.contrast")

(defn attributed!
  "Give the document's root the attribute `attr` with the value `v`, or
  else none, and keep a copy of it in this browser under the `key`, as it
  changes."
  [attr key v]
  (let [root js/document.documentElement]
    (when (not= v (.getAttribute root attr))
      (if v
        (do (.setAttribute root attr v)
            (local/store! key v))
        (do (.removeAttribute root attr)
            (local/forget! key))))))

;; Without preferences to read, the copies that public/theme.js applied are
;; the best guess at the listener's choice, so they stay
(defn themed!
  "Give the document the colours and the contrast that the listener chose
  in the `state`, as its data-theme and its data-contrast, or else none,
  for the device's, once the `state` has preferences to tell them by."
  [state]
  (when-let [{:keys [theme contrast]} (settings/look state)]
    (attributed! "data-theme" theme-key theme)
    (attributed! "data-contrast" contrast-key contrast)))

(defn bar-color
  "The colour of the player's bar as the browser computed it, or else the
  paper's, while nothing plays."
  []
  (->> ["#player" "body"]
       (keep #(js/document.querySelector %))
       (map #(.-backgroundColor (js/getComputedStyle %)))
       (remove #{"rgba(0, 0, 0, 0)" "transparent"})
       (first)))

;; HTML, the theme-color meta name: a colour for the browser's own bars,
;; e.g. Chrome's on Android, and for the title bar of an installed app. It
;; takes no var(), so it gets the bar's colour as the browser computed it,
;; in hex. Safari on iOS 26 reportedly ignores it.
;; https://html.spec.whatwg.org/multipage/semantics.html#meta-theme-color
;; https://benfrain.com/ios26-safari-theme-color-tab-tinting-with-fixed-position-elements/
(defn theme-colored!
  "Give the page's theme-color the colour of the player's bar, or of the
  paper, as it is now."
  []
  (when-let [meta (js/document.querySelector "meta[name=theme-color]")]
    (when-let [c (some-> (bar-color) cc/hex)]
      (when (not= c (.-content meta))
        (set! (.-content meta) c)))))

;; WCAG 2.2, SC 2.4.3 Focus Order: an element that a render takes away
;; while it has the focus leaves the focus on the body, where a keyboard
;; or a screen reader loses its place, e.g. an Undo that another tab left
;; nothing to undo
;; https://www.w3.org/WAI/WCAG22/Understanding/focus-order.html
(defn render!
  "Render the page for `state` into the element of the app, and give the
  window the page's title and the listener's colours. When the render
  takes away the element with the focus, the element that its
  data-refocus selector names takes the focus, if it names one."
  [state]
  (let [^js focused js/document.activeElement]
    (r/render (js/document.getElementById "app") (ui/page state))
    (when-let [selector (and focused
                             (not (.-isConnected focused))
                             (some-> focused .-dataset .-refocus))]
      (some-> (js/document.querySelector selector) .focus)))
  (set! js/document.title (ui/document-title state))
  (themed! state)
  (theme-colored!))

(defn canonicalize!
  "Write the canonical path of the page of the `state`, from
  ui/canonical-path, into the address bar, in place of the URL that the
  link gave, e.g. without a slug. It replaces the URL in the history, which adds no
  entry, and leaves the route of the state as it was, so that the focus
  doesn't move."
  [state]
  (when-let [path (ui/canonical-path state)]
    (when (not= path (str js/location.pathname js/location.search))
      (.replaceState js/history nil "" path))))

(defn focus!
  "Move the focus to the element `id`, where there's one."
  [id]
  (some-> (js/document.getElementById id) .focus))

(defn focus-heading!
  "Move the focus to the heading of the page."
  []
  (some-> (js/document.querySelector "main h1") .focus))

;; CSSOM View, scrollIntoView: a heading at the start of a row that
;; scrolls sideways, which brings its column into view, and at the top of
;; the page where it's out of sight, e.g. further down a stack, since focus
;; alone scrolls nothing where a part of the element shows
;; https://drafts.csswg.org/cssom-view/#dom-element-scrollintoview
(defn show!
  "Bring the heading `id` into view and move the focus to it."
  [id]
  (when-let [^js heading (js/document.getElementById id)]
    (let [box   (.getBoundingClientRect heading)
          seen? (and (>= (.-top box) 0) (<= (.-bottom box) js/window.innerHeight))]
      (.scrollIntoView heading #js {:block  (if seen? "nearest" "start")
                                    :inline "start"})
      (.focus heading #js {:preventScroll true}))))

;; HTML, scroll to the fragment, which a navigation by pushState leaves
;; undone: the start of the element at the top, as far as the page
;; scrolls. The focus moves there too, as the skip link moves it. A face
;; that the page needs only once it's laid out, e.g. the bold of a
;; heading, loads after the scroll and moves the text above the element,
;; so it scrolls again once the fonts are in, unless the focus moved on.
;; https://html.spec.whatwg.org/multipage/browsing-the-web.html#scroll-to-fragid
;; https://drafts.csswg.org/css-font-loading/#dom-fontfaceset-ready
(defn scroll-to!
  "Scroll the element `id` to the top of the page and move the focus to
  it, like a fragment of the URL."
  [id]
  (when-let [^js target (js/document.getElementById id)]
    (let [scroll! #(.scrollIntoView target #js {:block "start" :inline "nearest"})]
      (scroll!)
      (.focus target #js {:preventScroll true})
      (some-> js/document .-fonts .-ready
              (.then #(when (identical? target js/document.activeElement)
                        (scroll!)))))))

(defn open-dialog!
  "Open the dialog `id` as a modal one, with no answer yet."
  [id]
  (when-let [dialog (js/document.getElementById id)]
    (set! (.-returnValue dialog) "")
    (.showModal dialog)))
