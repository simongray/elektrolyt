(ns dk.simongray.podcast-app.main-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.main :as main]))

(def inbox
  {:name :route/inbox :path-params {}})

(def library
  {:name :route/library :path-params {}})

(deftest the-first-page-keeps-the-focus
  (is (= [[:store/assoc :route inbox]]
         (main/navigated {} inbox))))

(deftest another-page-takes-the-focus
  (is (= [[:store/assoc :route library] [:page/focus-heading]]
         (main/navigated {:route inbox} library)))
  (is (= [[:store/assoc :route inbox] [:page/focus-heading]]
         (main/navigated {:route nil} inbox))))

(deftest the-same-page-keeps-the-focus
  (is (= [[:store/assoc :route inbox]]
         (main/navigated {:route inbox} inbox))))
