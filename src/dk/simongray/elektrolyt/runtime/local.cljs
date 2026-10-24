(ns ^:runtime dk.simongray.elektrolyt.runtime.local
  "Values that the app keeps in this browser's localStorage. Where the
  browser blocks storage, e.g. in a private window, a read returns nil and
  a write does nothing."
  (:require [cljs.reader :as reader]))

(defn stored
  "The string that this browser keeps under `key`, or nil."
  [key]
  (try (.getItem js/localStorage key) (catch :default _ nil)))

(defn store!
  "Keep the string `s` under `key`."
  [key s]
  (try (.setItem js/localStorage key s) (catch :default _ nil)))

(defn forget!
  "Remove what this browser keeps under `key`."
  [key]
  (try (.removeItem js/localStorage key) (catch :default _ nil)))

(defn stored-number
  "The finite number that this browser keeps under `key`, or nil when
  there's none."
  [key]
  (let [n (js/parseFloat (stored key))]
    (when (js/Number.isFinite n)
      n)))

(defn stored-edn
  "The value that this browser keeps under `key` as EDN, or nil when
  there's none or it isn't EDN."
  [key]
  (try (some-> (stored key) reader/read-string) (catch :default _ nil)))

(defn store-edn!
  "Keep the value `v` under `key` as EDN."
  [key v]
  (store! key (pr-str v)))
