(ns dk.simongray.elektrolyt.ui.subscription
  "The bookmark that subscribes to a show, the mark of a show that's
  subscribed, and the error of a subscription that failed."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

(defn status
  "Where the subscription to the feed at `url` stands in `state`, given the
  `subscribed` URLs: :subscribed, :subscribing, :ready, or :waiting until
  the library is restored. With the show's `guid`, a subscription by that
  key counts too, e.g. one whose feed moved since."
  ([state subscribed url]
   (status state subscribed url nil))
  ([state subscribed url guid]
   (cond
     (nil? subscribed)                    :waiting
     (search/subscribed? subscribed url)  :subscribed
     (and guid (profile/subscribed? (:profile (:library state)) guid))
     :subscribed
     (contains? (:subscribing state) url) :subscribing
     :else                                :ready)))

;; The filled bookmark has Phosphor's microphone cut out of it, which
;; stands for podcasts, as the studio microphone emoji does, so that it
;; says what it keeps. The microphone's drawing, whose middle is at 128 by
;; 132 in its box, goes in the middle of the bookmark's body, at 112,
;; between the top and the point of the notch. The bookmark of a show to
;; subscribe to is the Thin weight's outline alone, whose lines are about
;; as thin as the microphone's.
;; https://emojipedia.org/studio-microphone
(def filled-bookmark
  "The bookmark of a show in the library, filled, with the microphone cut
  out of it."
  (let [scale 0.46]
    (str icons/bookmark-simple-fill " "
         (controls/placed icons/microphone-fill scale
                          (- 128 (* scale 128))
                          (- 112 (* scale 132))))))

(defn bookmark
  "The icon of the bookmark `path` in a box of the drawing alone, as the
  Fill weight draws it, so that the drawing's edges are the box's and can
  meet the lines of a title (layout.css). Its even-odd rule cuts out what
  lies inside the filled bookmark."
  [path]
  [controls/icon {:icon path :viewBox "56 32 144 199" :fill-rule "evenodd"}])

;; A bookmark keeps a show in the library, and fills in once it's there.
;; NN/g finds the star and the heart often misread, since sites use them
;; for favourites, bookmarks and ratings alike, and Pocket Casts stars
;; episodes. WCAG suggests the tooltip of an icon without words as its
;; name, so the button's words are both. They say what a press does, e.g.
;; "Unsubscribe from Podcasting 2.0", with no aria-pressed, by the APG's
;; button pattern. It's aria-disabled rather than disabled, so that it
;; keeps the focus as it changes, and it fills in at once while it
;; subscribes. Only a show's own page has the button: a list marks a show
;; that's subscribed and does nothing more, since unsubscribing drops the
;; show's episodes from the queue and the inbox, which a stray press in a
;; list would lose. The button comes before the show's title, outside the
;; heading, whose name would else take in its words (layout.css).
;; https://www.nngroup.com/articles/icon-usability/
;; https://support.pocketcasts.com/article/starring-episodes/
;; https://www.w3.org/WAI/WCAG22/Understanding/label-in-name.html
;; https://www.w3.org/WAI/ARIA/apg/patterns/button/
(defn button
  "The bookmark on the page of the show `title` at `url` that subscribes to
  it, or unsubscribes once it's subscribed, labelled by the subscription
  `status`."
  [status url title]
  (if (= :subscribed status)
    ;; it goes when the show had no page but for its subscription
    [controls/icon-button {:icon         (bookmark filled-bookmark)
                           :class        "bookmark"
                           :data-refocus "main h1"
                           :on           {:click (controls/single-click [[:library/unsubscribe url title]])}}
     "Unsubscribe from " title]
    (into [controls/icon-button (cond-> {:icon  (bookmark (if (= :subscribing status)
                                                            filled-bookmark
                                                            icons/bookmark-simple-thin))
                                         :class "bookmark"}
                                  (not= :ready status) (assoc :aria-disabled "true")
                                  (= :ready status)    (assoc :on {:click (controls/single-click
                                                                           [[:library/subscribe url title]])}))]
          (if (= :subscribing status)
            ["Subscribing to " title "…"]
            ["Subscribe to " title]))))

(defn marked
  "The `title` of a show that's subscribed, e.g. its link, in a list that
  has no button, e.g. of search results: after the button's filled
  bookmark, and with words for a screen reader, which skips the icon."
  [title]
  (list [controls/icon {:icon icons/bookmark-simple-fill :class "bookmark" :title "Subscribed"}]
        " " title
        [:span.visually-hidden ", subscribed"]))

(defn error
  "The paragraph that says why the subscription to the feed at `url`
  failed in `state`, or nil."
  [state url]
  (when-let [e (get-in state [:subscribe-errors url])]
    [:p "Couldn't subscribe: " (:message e)]))
