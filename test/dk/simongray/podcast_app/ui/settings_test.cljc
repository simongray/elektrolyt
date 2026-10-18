(ns dk.simongray.podcast-app.ui.settings-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.ui.settings :as settings-ui]
            [dk.simongray.podcast-app.ui.show-test :refer [text-of]]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(defn by-id
  "The element of the Hiccup `x` with the `tag` and the `id`."
  [x tag id]
  (some #(when (= id (:id (second %))) %) (elements tag x)))

(defn chosen
  "The value of the option that the select `id` of the Hiccup `x` has
  selected."
  [x id]
  (some #(when (:selected (second %)) (:value (second %)))
        (elements :option (by-id x :select id))))

(deftest the-settings-page
  (let [x (settings-ui/content {:library inbox-test/lib})]
    (is (= ["Playback" "New episodes" "Search" "Subscriptions" "This browser"]
           (map text-of (elements :h2 (elements :legend x))))
        "each group a fieldset, its heading in its legend")
    (is (= [["playback" -1] ["new-episodes" -1] ["search" -1] ["subscriptions" -1] ["this-browser" -1]]
           (map #((juxt :id :tabindex) (second %)) (elements :h2 x)))
        "which a link to the group names, and which takes the focus")
    (is (= "/settings#search" (settings-ui/group-href :search)))
    (testing "the preferences as they are, the defaults at first"
      (is (= "15" (chosen x "skip-back")))
      (is (= "30" (chosen x "skip-forward")))
      (is (= "1" (chosen x "speed")))
      (is (= "chapters" (chosen x "track-buttons")))
      (is (= "inbox" (chosen x "default-new-episodes"))))
    (testing "each change applied at once"
      (is (= [[:settings/prefer :skip-back [:event/target-number]]]
             (get-in (by-id x :select "skip-back") [1 :on :change])))
      (is (= [[:settings/new-episodes [:event/target-keyword]]]
             (get-in (by-id x :select "default-new-episodes") [1 :on :change]))))
    (let [[_ attrs] (by-id x :input "continuous")]
      (is (= ["checkbox" "switch" false] ((juxt :type :role :checked) attrs))
          "continuous play, a switch")
      (is (= [[:settings/prefer :continuous? [:event/target-checked]]] (get-in attrs [:on :change]))))
    (is (= 3 (count (filter #(= "status" (:role (second %))) (elements :p.settings-status x))))
        "a status line beside the buttons of each group that has them")))

(deftest the-directories-of-a-search
  (let [x    (settings-ui/search-directories {:library inbox-test/lib})
        rows (elements :li.setting x)]
    (is (= ["Podcast Index" "Apple Podcasts" "fyyd" "gpodder.net"] (map text-of (elements :label x)))
        "those that a search asks, in their order, then the others")
    (is (= [true true true false] (map #(:checked (second (by-id % :input (:id (second (first (elements :input %))))))) rows)))
    (is (re-find #"This server doesn't sign" (text-of (first rows)))
        "the Podcast Index says where the server can't search it")
    (testing "the rows that a search asks move, as those of the queue do"
      (is (= "true" (:aria-disabled (second (by-id x :button "directory-podcastindex-up")))) "no way up from the top")
      (is (= [[:settings/move-source :apple 3] [:page/focus "directory-apple-down"]]
             (get-in (by-id x :button "directory-apple-down") [1 :on :click])))
      (is (empty? (elements :button (last rows))) "but not those that it leaves out"))
    (is (= [[:settings/search-source :gpodder [:event/target-checked]] [:page/focus "directory-gpodder"]]
           (get-in (by-id x :input "directory-gpodder") [1 :on :change])))))

(deftest how-this-browser-keeps-the-library
  (let [shown #(text-of (settings-ui/this-browser {:storage %}))]
    (is (re-find #"Asking the browser" (shown nil)))
    (is (re-find #"may clear the library.*Ask the browser to keep it"
                 (shown {:supported? true :persisted? false :usage 1234567})))
    (is (re-find #"keeps the library until you clear.*1\.2 MB"
                 (shown {:supported? true :persisted? true :usage 1234567})))
    (is (re-find #"doesn't say" (shown {:supported? false}))))
  (let [button (->> (elements :button (settings-ui/this-browser {:storage nil}))
                    (some #(when (= [[:cache/clear]] (get-in % [1 :on :click])) %)))]
    (is (= "Clear the searches and shows" (last button))
        "and a button that clears what the cache keeps")))
