(ns ^:runtime dk.simongray.elektrolyt.local
  "What this browser keeps in localStorage for the app, apart from the
  library: values that belong to the device rather than to the listener,
  e.g. the layout of the Library page, which a screen suits.

  Where the browser keeps nothing, e.g. in a private window that blocks
  storage, a read gives nil and a write does nothing."
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
  "Forget what this browser keeps under `key`."
  [key]
  (try (.removeItem js/localStorage key) (catch :default _ nil)))

(defn stored-number
  "The finite number that this browser keeps under `key`, or nil, also for
  what isn't one."
  [key]
  (let [n (js/parseFloat (stored key))]
    (when (js/Number.isFinite n)
      n)))

(defn stored-edn
  "The value that this browser keeps under `key` as EDN, or nil, also for
  what isn't EDN."
  [key]
  (try (some-> (stored key) reader/read-string) (catch :default _ nil)))

(defn store-edn!
  "Keep the value `v` under `key` as EDN."
  [key v]
  (store! key (pr-str v)))
