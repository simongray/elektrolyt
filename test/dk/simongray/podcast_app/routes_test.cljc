(ns dk.simongray.podcast-app.routes-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.routes :as routes]
            [reitit.core :as reitit]))

(def pc20
  "The guid of Podcasting 2.0."
  "917393e3-1b1e-5cef-ace4-edaa54e1f810")

(defn route-of
  "The route of the `path`, as the state keeps it."
  [path]
  (routes/match->route (reitit/match-by-path routes/router path)))

(deftest every-page-matches-its-own-href
  (doseq [route-name [:route/queue :route/inbox :route/library :route/search :route/settings]]
    (is (= {:name route-name :path-params {} :query-params {}}
           (route-of (routes/href route-name))))))

(deftest unknown-path-has-no-route
  (is (nil? (route-of "/nowhere"))))

(deftest a-page-with-another-query-is-the-same-page
  (let [search {:name :route/search :path-params {} :query-params {:q "a"}}]
    (is (routes/same-page? search (assoc search :query-params {:q "b"})))
    (is (routes/same-page? search (assoc search :fragment "apple")))
    (is (not (routes/same-page? search (assoc search :name :route/inbox))))))

(deftest a-link-to-a-part-of-a-page
  (is (= "/settings#search" (routes/href :route/settings nil nil "search")))
  (is (= {:name :route/settings :path-params {} :query-params {} :fragment "search"}
         ;; reitit.frontend's match, unlike reitit.core's, has the fragment
         (routes/match->route (assoc (reitit/match-by-path routes/router "/settings")
                                     :fragment "search"))))
  (is (not (contains? (route-of "/settings") :fragment))))

(deftest the-slug-of-a-title
  (is (= "podcasting-2-0" (routes/slug "Podcasting 2.0")))
  (is (= "jagten-pa-det-evige-liv" (routes/slug "Jagten på det evige liv")) "accents taken off")
  (is (= "morkeland-aero" (routes/slug "Mørkeland: Ærø!")) "and the letters without accents folded")
  (is (nil? (routes/slug "日本語")) "no slug of a title without Latin letters or digits")
  (is (<= (count (routes/slug (apply str (repeat 20 "Long title ")))) 60))
  (is (nil? (routes/slug nil))))

(deftest the-page-of-a-show
  (is (= (str "/shows/" pc20 "/podcasting-2-0") (routes/show-href pc20 "Podcasting 2.0")))
  (is (= (str "/shows/" pc20) (routes/show-href pc20 "日本語")))
  (is (= {:name :route/show :path-params {:guid pc20} :query-params {}}
         (dissoc (route-of (str "/shows/" pc20 "/podcasting-2-0")) :slug)
         (dissoc (route-of (str "/shows/" pc20 "/another-slug")) :slug)
         (route-of (str "/shows/" pc20)))
      "the guid finds the show with any slug or none")
  (is (routes/same-page? (route-of (str "/shows/" pc20 "/a")) (route-of (str "/shows/" pc20)))))

(deftest the-page-of-an-episode
  (let [item "https://example.org/?p=7"
        h    "84b5b7c"]
    (is (= (str "/shows/" pc20 "/episodes/" h "/episode-7") (routes/episode-href pc20 item "Episode 7" nil))
        "the short hash of a guid that couldn't go in a path")
    (is (= (str "/shows/" pc20 "/episodes/" h "?url=https%3A%2F%2Fexample.org%2Ffeed.xml&t=754")
           (routes/episode-href pc20 item "日本語" "https://example.org/feed.xml" 754.5))
        "with the feed of a show that the library doesn't keep, and a time")
    (is (= {:name :route/episode :path-params {:guid pc20 :hash h} :query-params {} :slug "episode-7"}
           (route-of (str "/shows/" pc20 "/episodes/" h "/episode-7")))
        "the slug apart from the path, to tell two episodes of a hash apart")
    (is (routes/same-page? (route-of (str "/shows/" pc20 "/episodes/" h "/a"))
                           (route-of (str "/shows/" pc20 "/episodes/" h))))
    (is (= :route/show (:name (route-of (str "/shows/" pc20 "/episodes")))) "a show's slug can be anything")))
