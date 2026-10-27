(ns dk.simongray.elektrolyt.library.options
  "Where the listener's library is kept and how it fetches, the same in the
  page and its web worker.

  The worker requires this rather than dk.simongray.elektrolyt.library,
  which would bring the page's runtime into the worker."
  (:require [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.wary-fetch.proxy :as proxy]))

(def send
  "The function that sends the library's requests through the proxy on the
  app's server."
  (proxy/sender "/proxy"))

(def store
  "The library's IndexedDB database."
  (indexeddb/store "dk.simongray.elektrolyt.library"))

;; The app offers no choice of where new episodes go, so they all go to the
;; inbox. A show's own choice from an earlier version or another app stays
;; in its settings, for the apps that offer one.
;;
;; Hosts often put an episode's notes in three or four fields at once, and
;; the app shows only the one that resolve/description reads, so a lean
;; library keeps that one alone in memory and in a feed's document, and
;; the others in a document beside it: a library of 53 shows took 268 MB
;; of heap after the start, and 173 MB lean
(def config
  "The library's config, the same in every tab and the worker."
  {:new-episodes :inbox
   :lean?        true})

(def options
  "The options of every call to the library: its requests go through the
  proxy and its documents are in Transit. They're functions, so they stay
  out of its config."
  {:send  send
   :codec transit/codec})

(def document-name
  "The name that podcast-clj gives the library's own document in the store."
  (str "library" (:extension transit/codec)))
