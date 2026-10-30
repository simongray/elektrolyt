(ns dk.simongray.elektrolyt.ui.document
  "What the app does to the document: renders the page into it, gives the
  window its title, address and colours, and moves the focus and the
  scroll."
  (:require [dk.simongray.colour-coded :as cc]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.local :as local]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [replicant.dom :as r]))

(def theme-key
  "The localStorage key of a copy of the listener's theme, which
  public/theme.js applies before the first render."
  "dk.simongray.elektrolyt.theme")

(def contrast-key
  "The localStorage key of a copy of the listener's contrast, which
  public/theme.js applies too."
  "dk.simongray.elektrolyt.contrast")

(defn attributed!
  "Set the attribute `attr` of the document's root to `v`, or remove it for
  nil, and keep a copy in localStorage under `key` when it changes."
  [attr key v]
  (let [root js/document.documentElement]
    (when (not= v (.getAttribute root attr))
      (if v
        (do (.setAttribute root attr v)
            (local/store! key v))
        (do (.removeAttribute root attr)
            (local/forget! key))))))

(defn themed!
  "Set the document's data-theme and data-contrast to the listener's
  choices in `state`, or remove them to follow the device. Without
  preferences in `state`, the copies from localStorage stay as the best
  guess."
  [state]
  (when-let [{:keys [theme contrast]} (settings/look state)]
    (attributed! "data-theme" theme-key theme)
    (attributed! "data-contrast" contrast-key contrast)))

(defn bar-color
  "The computed colour of the player's bar, or else of the paper while
  nothing plays."
  []
  (->> ["#player" "body"]
       (keep #(js/document.querySelector %))
       (map #(.-backgroundColor (js/getComputedStyle %)))
       (remove #{"rgba(0, 0, 0, 0)" "transparent"})
       (first)))

;; HTML, the theme-color meta name: a colour for the browser's own bars,
;; e.g. Chrome's on Android, and an installed app's title bar. It takes no
;; var(), so it gets the computed colour in hex. Safari on iOS 26
;; reportedly ignores it.
;; https://html.spec.whatwg.org/multipage/semantics.html#meta-theme-color
;; https://benfrain.com/ios26-safari-theme-color-tab-tinting-with-fixed-position-elements/
(defn theme-colored!
  "Set the page's theme-color to the current colour of the player's bar,
  or else of the paper."
  []
  (when-let [meta (js/document.querySelector "meta[name=theme-color]")]
    (when-let [c (some-> (bar-color) cc/hex)]
      (when (not= c (.-content meta))
        (set! (.-content meta) c)))))

(defonce ^{:doc "Whether a reading of the theme colour waits for the next
  paint."}
  theme-waits
  (atom false))

;; Reading a computed colour right after a render makes the browser work
;; out the page's styles there and then, which the next render of the
;; same task, e.g. of a Play click, undoes
;; https://gist.github.com/paulirish/5d52fb081b3570c81e3a
(defn theme-colored-soon!
  "Set the page's theme-color once the page has painted, once for every
  render before then."
  []
  (when (compare-and-set! theme-waits false true)
    (.then (runtime/painted!)
           (fn [_]
             (reset! theme-waits false)
             (theme-colored!)))))

;; HTML, the icon link type: the page's icon in the browser's tab, or else
;; the browser's own
;; https://html.spec.whatwg.org/multipage/links.html#rel-icon
(defn favicon!
  "Set the page's icon to `href`, or remove it for nil. The page changes
  only when the icon does, since it renders every second during playback."
  [href]
  (let [link (js/document.querySelector "link[rel=icon]")]
    (cond
      (nil? href)                            (some-> link .remove)
      (nil? link)                            (.append js/document.head
                                                      (doto (js/document.createElement "link")
                                                        (.setAttribute "rel" "icon")
                                                        (.setAttribute "href" href)))
      (not= href (.getAttribute link "href")) (.setAttribute link "href" href))))

;; WCAG 2.2, SC 2.4.3 Focus Order: when a render removes the focused
;; element, the focus falls to the body, where a keyboard or a screen
;; reader loses its place, e.g. on an Undo that another tab left nothing
;; to undo
;; https://www.w3.org/WAI/WCAG22/Understanding/focus-order.html
(defn render!
  "Render the page for `state`, and give the window its title, icon and
  colours. When the render removes the focused element, the focus moves
  to the element that its data-refocus selector names, if any."
  [state]
  (let [^js focused js/document.activeElement]
    (r/render (js/document.getElementById "app") (ui/page state))
    ;; by its name, since a release build renames a key of the dataset
    (when-let [selector (and focused
                             (not (.-isConnected focused))
                             (.getAttribute focused "data-refocus"))]
      (some-> (js/document.querySelector selector) .focus)))
  (let [title (ui/document-title state)]
    (when (not= title js/document.title)
      (set! js/document.title title)))
  (favicon! (ui/favicon state))
  (themed! state)
  (theme-colored-soon!))

;; ClojureScript's atoms tell their watches of a swap that changes nothing
;; too, e.g. the player's answer to a Play, which it keeps only when it's a
;; refusal. A hidden page, e.g. with the screen off as an episode plays,
;; would render once a second for nobody to see the position move.
;; https://developer.mozilla.org/en-US/docs/Web/API/Document/visibilityState
(defn rendered!
  "Render the page for the `new` state and return true, unless it's the
  `old` one, or the page is hidden and only the player's clock changed,
  which a render once it's shown catches up on."
  [old new]
  (when-not (or (identical? old new)
                (and (not (runtime/visible?)) (player/clock-only? old new)))
    (render! new)
    true))

(defn canonicalize!
  "Replace the URL in the address bar with the canonical path of the page
  in `state`, e.g. one with a slug, without a new history entry. The route
  in `state` stays as it was, so the focus doesn't move."
  [state]
  (when-let [path (ui/canonical-path state)]
    (when (not= path (str js/location.pathname js/location.search))
      (.replaceState js/history nil "" (str path js/location.hash)))))

;; A stray % typed into the address bar would make the router throw, and
;; leave the page blank
(defn decodable!
  "Replace the URL in the address bar with one that the router can decode,
  e.g. with a stray % escaped, without a new history entry."
  []
  (let [url   (str js/location.pathname js/location.search js/location.hash)
        fixed (routes/decodable url)]
    (when (not= url fixed)
      (.replaceState js/history nil "" fixed))))

(defn focus!
  "Move the focus to the element `id`, where there's one."
  [id]
  (some-> (js/document.getElementById id) .focus))

(defn focus-heading!
  "Move the focus to the heading of the page."
  []
  (some-> (js/document.querySelector "main h1") .focus))

(defn restore-scroll!
  "Scroll the main content back to `px` and move the focus to the page's
  heading without scrolling to it."
  [px]
  (some-> (js/document.querySelector "main h1") (.focus #js {:preventScroll true}))
  (when-let [main (js/document.getElementById "main")]
    (set! (.-scrollTop main) px)))

;; CSSOM View, scrollIntoView: the heading goes to the start of a row that
;; scrolls sideways, which brings its column into view, and to the top of
;; the page when it's out of sight, e.g. further down a stack. Focus alone
;; scrolls nothing while part of the element shows. The scroll is smooth
;; where motion is allowed, so that the listener sees which way it went;
;; auto is at once, since no CSS sets a scroll-behavior.
;; https://drafts.csswg.org/cssom-view/#dom-element-scrollintoview
(defn show!
  "Bring the heading `id` into view and move the focus to it."
  [id]
  (when-let [^js heading (js/document.getElementById id)]
    (let [box   (.getBoundingClientRect heading)
          seen? (and (>= (.-top box) 0) (<= (.-bottom box) js/window.innerHeight))]
      (.scrollIntoView heading #js {:block    (if seen? "nearest" "start")
                                    :inline   "start"
                                    :behavior (if (runtime/motion?) "smooth" "auto")})
      (.focus heading #js {:preventScroll true}))))

;; HTML, scroll to the fragment, which a pushState navigation doesn't do.
;; A font face that loads after the scroll, e.g. a heading's bold, can move
;; the text above the element, so it scrolls again once the fonts are in,
;; unless the focus has moved on.
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
  "Open the dialog `id` as a modal, with its return value cleared."
  [id]
  (when-let [dialog (js/document.getElementById id)]
    (set! (.-returnValue dialog) "")
    (.showModal dialog)))

(defn flight
  "The keyframes of a copy of the box `a` that flies into the box `b` and
  fades out as it gets there, as big as the shorter side of `b`, in its
  middle."
  [^js a ^js b]
  (let [s (/ (min (.-width b) (.-height b)) (.-width a))
        x (- (+ (.-left b) (/ (- (.-width b) (* s (.-width a))) 2)) (.-left a))
        y (- (+ (.-top b) (/ (- (.-height b) (* s (.-height a))) 2)) (.-top a))]
    #js [#js {:transform "none" :opacity 1}
         #js {:opacity 1 :offset 0.7}
         #js {:transform (str "translate(" x "px, " y "px) scale(" s ")") :opacity 0}]))

;; NN/g: motion makes a change seen, which change blindness can else hide,
;; and stays brief. FLIP, after Paul Lewis: the copy moves by its transform
;; and fades by its opacity alone, which the browser animates without
;; laying out the page again. It takes no clicks and goes once it has
;; landed, as the target swells a little to take it.
;; https://www.nngroup.com/articles/animation-purpose-ux/
;; https://aerotwist.com/blog/flip-your-animations/
;; https://developer.mozilla.org/en-US/docs/Web/API/Element/animate
(defn fly!
  "Send a copy of the element at the selector `from` into the first element
  in the page at the `to` selectors, where it fades out, unless the
  listener asks for less motion."
  [from to]
  (let [^js source (js/document.querySelector from)
        ^js target (some #(js/document.querySelector %) to)]
    (when (and source target (runtime/motion?))
      (let [a        (.getBoundingClientRect source)
            ^js copy (.cloneNode source true)]
        (set! (.. copy -style -cssText)
              (str "position: fixed; z-index: 2; margin: 0; pointer-events: none;"
                   " transform-origin: 0 0; left: " (.-left a) "px; top: " (.-top a) "px;"
                   " width: " (.-width a) "px; height: " (.-height a) "px"))
        (.append js/document.body copy)
        (let [done #(.remove copy)]
          (-> (.animate copy (flight a (.getBoundingClientRect target))
                        #js {:duration 500 :easing "ease-in-out"})
              .-finished
              (.then done done)))
        (.animate target #js [#js {:scale 1} #js {:scale 1.08} #js {:scale 1}]
                  #js {:duration 200 :delay 400 :easing "ease-out"})))))

;; WCAG 2.2, SC 1.4.13 Content on Hover or Focus: what shows on hover or
;; focus hides on Escape without the pointer or the focus moving, as
;; Video.js hides its volume. The focus goes to the element's button, since
;; it mustn't stay in a hidden part. The mark is an attribute that the
;; views never set, so that a render keeps it. A popover with interestfor
;; would do this, but only Chrome and Edge have it so far.
;; https://www.w3.org/WAI/WCAG22/Understanding/content-on-hover-or-focus.html
;; https://caniuse.com/wf-interest-invokers
(defn dismiss-on-escape!
  "Hide what the element of the keyboard `event` shows on hover and focus
  when the key is Escape, and move the focus to its button."
  [^js event]
  (when (= "Escape" (.-key event))
    (let [element (.-currentTarget event)]
      (.setAttribute element "data-dismissed" "")
      (some-> (.querySelector element "button") .focus))))

(defn restore-on-leave!
  "Let the element of the pointer or focus `event` show on hover and focus
  again, once neither the pointer nor the focus is inside it."
  [^js event]
  (let [element  (.-currentTarget event)
        hovered? (and (not= "pointerleave" (.-type event)) (.matches element ":hover"))
        focused  (if (= "focusout" (.-type event)) (.-relatedTarget event) js/document.activeElement)]
    (when-not (or hovered? (.contains element focused))
      (.removeAttribute element "data-dismissed"))))
