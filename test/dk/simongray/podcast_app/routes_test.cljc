(ns dk.simongray.podcast-app.routes-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.routes :as routes]
            [reitit.core :as reitit]))

(deftest every-route-matches-its-own-href
  (doseq [route-name (reitit/route-names routes/router)]
    (is (= {:name route-name :path-params {}}
           (->> (routes/href route-name)
                (reitit/match-by-path routes/router)
                (routes/match->route))))))

(deftest unknown-path-has-no-route
  (is (nil? (routes/match->route
             (reitit/match-by-path routes/router "/nowhere")))))
