(ns dk.simongray.elektrolyt.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.operations :as operations]
            [dk.simongray.elektrolyt.player-test :as player-test]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]))

(defn nodes
  "The nodes of the Hiccup `x`, itself first, at any depth."
  [x]
  (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x))

(defn elements
  "The elements of the Hiccup `x` with the `tag`, at any depth."
  [tag x]
  (filter #(and (vector? %) (= tag (first %))) (nodes x)))

(defn lang-of
  "The lang of the first element of the Hiccup `x` with the `tag`."
  [tag x]
  (:lang (second (first (elements tag x)))))

(defn text-of
  "The text of the Hiccup `x`, as one string."
  [x]
  (apply str (filter string? (nodes x))))

(defn spoken
  "The text of the Hiccup `x` that a screen reader reads, as one string:
  all of it but what's aria-hidden."
  [x]
  (let [hidden? #(and (vector? %) (= "true" (:aria-hidden (second %))))]
    (->> (tree-seq #(and ((some-fn vector? seq?) %) (not (hidden? %)))
                   #(if (vector? %) (rest %) %)
                   x)
         (filter string?)
         (apply str))))

(defn current-links
  "The labels of the links that the Hiccup `x` marks as current, as the
  page or else as `kind`, e.g. \"true\"."
  ([x]
   (current-links x "page"))
  ([x kind]
   (->> (elements :a x)
        (filter #(= kind (:aria-current (second %))))
        (map text-of))))

(deftest the-current-page-is-marked-once
  (is (= ["Home"]
         (current-links (ui/page {:route {:name :route/home}}))))
  (is (= ["Settings"]
         (current-links (ui/page {:route {:name :route/settings}}))))
  (is (empty? (current-links (ui/page {:route nil}))))
  (let [inbox (ui/page {:route {:name :route/inbox}})]
    (is (empty? (current-links inbox)) "the Inbox page, which the navigation doesn't link to")
    (is (= ["Library"] (current-links inbox "true")) "but whose Library is marked")))

(deftest the-window-title-starts-with-the-page
  (is (= "Library – Elektrolyt"
         (ui/document-title {:route {:name :route/library}})))
  (is (= "Page not found – Elektrolyt"
         (ui/document-title {:route nil})))
  (is (= "Elektrolyt" (ui/document-title {:route {:name :route/home}}))
      "and the app's name alone on Home, as its heading is"))

(deftest the-landmarks-keep-their-order
  (let [state (assoc (player-test/state {}) :route {:name :route/queue})]
    (is (= [:a.skip-link :nav :main#main :section#player]
           (map first (drop 2 (ui/page state))))
        "after the shell's attributes")
    (is (= [:a.skip-link :nav :main#main]
           (map first (remove nil? (drop 2 (ui/page (dissoc state :player))))))
        "and the player only while it has an episode")))

(deftest the-colours-of-the-artwork
  (let [playing "https://example.org/a.jpg"
        state   (assoc (player-test/state {}) :route {:name :route/queue})]
    (is (= {:data-palette "pages"} (second (ui/page state)))
        "the app's own colours, while the artwork's are unknown")
    (is (= {:data-palette "pages" :style {:--bar-seed "#9e2a2b"}}
           (second (ui/page (assoc state :colors {playing {:seed "#9e2a2b"}}))))
        "and the player in the seed of the artwork of what plays, once it's known")
    (is (= {:data-palette "pages"}
           (second (ui/page (assoc state :colors {playing {:seed nil}}))))
        "or the app's own, for artwork without a hue")))

(deftest each-link-of-the-navigation-names-its-page
  (is (= ["home" "queue" "library" "search" "settings"]
         (map (comp :data-page second) (elements :li (ui/main-nav {:route {:name :route/queue}})))))
  (is (= ["queue" "library" nil]
         (map #(:data-page (second (ui/main-nav {:route {:name %}})))
              [:route/queue :route/inbox :route/show]))
      "and the navigation names the page that it's on, for its tone, e.g. the Library on the Inbox page")
  (let [preferring #(-> {:route {:name :route/queue} :library inbox-test/lib}
                        (operations/changed (settings/preferred {::app/navigation-palette %})))]
    (is (= "color-blind" (:data-palette (second (ui/page (preferring :color-blind)))))
        "from the colours for colour blindness, when the listener chose them")
    (is (every? nil? (map (comp :data-page second) (elements :li (ui/main-nav (preferring :none)))))
        "or none, when the listener chose none")))

(deftest each-link-of-the-navigation-has-its-pages-title-and-icon
  (let [links (elements :a (ui/main-nav {:route {:name :route/queue}}))]
    (is (= ["Home" "Queue" "Library" "Search" "Settings"] (map text-of links)))
    (is (= (map ui/nav-icons ui/destinations)
           (map #(:icon (second (first (elements controls/icon %)))) links))
        "after the title, which a phone shows only to a screen reader")))

(deftest the-canonical-path-of-a-show
  (let [url     "https://feeds.podcastindex.org/pc20.xml"
        derived "d5a1ae35-5e8b-599a-943b-95609562016d"
        route   {:name :route/show :path-params {:guid derived} :query-params {:url url}}
        found   {:searches {"pc20" {:results [{:guid derived :url url :title "Podcasting 2.0"}]}}}]
    (is (= (str "/shows/" derived "/podcasting-2-0?url=https%3A%2F%2Ffeeds.podcastindex.org%2Fpc20.xml")
           (ui/canonical-path (assoc found :route route :library (library/make "test"))))
        "a show that the library doesn't keep keeps the URL of its feed")
    (let [lib (library/subscribe (library/make "test") url {:rss/title "Podcasting 2.0"})
          k   (key (first (library/subscriptions lib)))]
      (is (= (str "/shows/" k "/podcasting-2-0")
             (ui/canonical-path (assoc found :route route :library lib)))
          "and a show that it keeps has its key there and no URL"))
    (is (nil? (ui/canonical-path {:route route})) "before the title is known")))

(deftest the-library-pages-without-a-library
  (is (every? #(some #{ui/no-library} (elements :p (ui/main-content {:route {:name %}})))
              ui/library-pages)
      "each page that needs it says that it couldn't be read"))
