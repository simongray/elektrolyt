(ns dk.simongray.elektrolyt.show.feeds-test
  (:require [clojure.test :refer [async deftest is]]
            [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.like-minded.store :as store]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.show :as app.show]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.feeds :as feeds]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]))

(def url
  "The URL of the feed of the show g of the tests."
  "https://example.org/feed.xml")

(def fed
  "The feed of the show g of the tests, with an episode."
  {:rss/title "A Show" :rss/items [{:rss/title "One" :rss/guid {:content "1"}}]})

(def looked-up-guid
  "The guid of the show that the tests look up in the Podcast Index."
  "917393e3-1b1e-5cef-ace4-edaa54e1f810")

(defn looked-up
  "A promise of the state once the Podcast Index was asked for the show
  looked-up-guid and answered with `answer`."
  [answer]
  (let [store (atom {:server {:podcastindex? true}})]
    (with-redefs [app-cache/send (fn [_] answer)]
      (.then (feeds/look-up-show! store looked-up-guid false true) (fn [_] @store)))))

(deftest a-lookup-in-the-podcast-index
  (is (not (feeds/look-up? {:server {:podcastindex? true}} "917393e3" nil))
      "which can't find a show by the start of its guid")
  (async done
    (-> (looked-up (js/Promise.resolve {:status 404}))
        (.then (fn [state]
                 (is (= {:not-found? true} (get-in state [:feed-urls looked-up-guid])))
                 (is (not (feeds/look-up? state looked-up-guid nil)) "which isn't asked again")))
        (.then #(looked-up (js/Promise.reject (js/Error. "Offline"))))
        (.then (fn [state]
                 (is (nil? (get-in state [:feed-urls looked-up-guid])) "but one that failed leaves nothing")
                 (is (feeds/look-up? state looked-up-guid nil) "so the next visit asks again")))
        (.catch #(is false (str "rejected: " %)))
        (.finally done))))

(defn checked
  "A promise of the state once the cached feed of the show g was fetched
  again and the fetch answered with `answer`."
  [answer]
  (let [store (atom {:feeds {url {:feed fed :whole? true :checked? false}}})]
    (with-redefs [fetching/fetch! (fn [_ _] (js/Promise.resolve answer))]
      (.then (feeds/check-show! store "g" url) (fn [_] @store)))))

(deftest a-cached-feed-fetched-again
  (async done
    (-> (checked {:status 200 :url url :feed (assoc fed :rss/items [])})
        (.then (fn [state]
                 (is (= fed (get-in state [:feeds url :feed]))
                     "keeps its episodes when the feed comes back without any")))
        (.then #(checked {:status 404 :url url :error {:id :fetch/status :status 404}}))
        (.then (fn [state]
                 (is (= {:id :fetch/status :status 404} (get-in state [:feeds url :error]))
                     "and a feed that's gone fails as a fetch of the whole feed does")))
        (.catch #(is false (str "rejected: " %)))
        (.finally done))))

(deftest a-show-cached-as-the-worker-sent-it
  (async done
    (let [fetched {:status 200 :url url :feed fed :etag "\"v1\""
                   :websub {:hubs ["https://hub.example/"]}}
          s       ((:encode (:codec library-options/options)) fetched)
          k       (show/cache-key url)
          puts    (atom [])
          mem     (store/memory)]
      (is (identical? (:codec library-options/options) (:codec app-cache/shows))
          "in the library's codec, in which the worker encodes it")
      (with-redefs [app-cache/put! (fn [c k v] (swap! puts conj [c k v]))]
        (feeds/keep-show! url (vary-meta fetched assoc ::fetching/encoded s))
        (feeds/keep-show! url fetched))
      (is (= [[app-cache/encoded-shows k s] [app-cache/shows k (show/kept fetched)]] @puts)
          "as the string it came as, or else encoded")
      (-> (cache/put! (assoc app-cache/encoded-shows :store mem) k s)
          (.then #(cache/fetch! (assoc app-cache/shows :store mem) k))
          (.then (fn [{:keys [value]}]
                   (is (= (show/kept fetched) (show/kept value))
                       "which reads back as the feed it keeps")))
          (.catch #(is false (str "rejected: " %)))
          (.finally done)))))

(deftest a-feed-gone-from-the-url-that-the-cache-had
  (async done
    (let [gone  {:id :fetch/status :status 404}
          store (atom {:feeds     {url {:feed fed :whole? true :checked? true}}
                       :feed-urls {"g" {:url url :from :cache}}})]
      (with-redefs [feeds/look-up-show! (fn [_ _ _ _])]
        (feeds/show-failed! store "g" url gone))
      (is (nil? (get-in @store [:feed-urls "g"])) "asks the Podcast Index again")
      (is (not (get-in @store [:feeds url :checked?])) "and checks the feed again")
      ;; the index answers with the same URL
      (swap! store assoc-in [:feed-urls "g"] {:url url :from :index})
      (with-redefs [fetching/fetch! (fn [_ _] (js/Promise.resolve {:status 404 :url url :error gone}))]
        (-> (feeds/load-show! store "g" url false)
            (.then (fn [_]
                     (is (= gone (get-in @store [:feeds url :error]))
                         "where it fails at the same URL, as a fetch of the whole feed does")))
            (.catch #(is false (str "rejected: " %)))
            (.finally done))))))

(deftest the-other-shows-of-a-publisher
  (async done
    (let [own     "917393e3-1b1e-5cef-ace4-edaa54e1f810"
          ;; a show of the podroll whose feed failed, so that nothing is
          ;; cached
          fetched {:podroll   [{:feed-guid "g1" :error {:status 404}}]
                   :publisher {:feed       {:rss/title "Podnews LLC"}
                               :shows      [{:feed-guid own} {:feed-guid "g3" :title "Podnews Daily"}]
                               :confirmed? true}}]
      ;; with both of its arities, since ClojureScript calls a function of
      ;; several arities at the arity itself
      (with-redefs [app.show/fetch-linked-shows! (fn
                                                   ([_] (js/Promise.resolve fetched))
                                                   ([_ _] (js/Promise.resolve fetched)))]
        (-> (feeds/linked-shows! "g" (assoc fed :podcast/guid own) url nil)
            (.then (fn [shows]
                     (is (= ["g3"] (map :guid (get-in shows [:publisher :shows])))
                         "leave out this show by the guid that its feed declares, where its page goes by another")))
            (.catch #(is false (str "rejected: " %)))
            (.finally done))))))

(deftest a-subscription-whose-fetch-failed
  (async done
    (let [url   "https://example.org/feed.xml"
          store (atom {:library (library/make "test")})]
      (with-redefs [fetching/fetch! (fn [_ _] (js/Promise.reject (js/Error. "The worker failed")))]
        (-> (feeds/subscribe! store url "A Show")
            (.then (fn [_]
                     (is (empty? (:subscribing @store)) "isn't under way any longer")
                     (is (= {:message "The worker failed"} (get-in @store [:subscribe-errors url]))
                         "but failed, as a fetch that answers with an error does")
                     (is (= "Couldn't subscribe to A Show" (:announcement @store)))))
            (.catch #(is false (str "rejected: " %)))
            (.finally done))))))

(deftest a-subscription-whose-documents-the-worker-built
  (async done
    (let [store    (atom {:library (library/make "test" {:config {:search? true}})})
          asked    (atom [])
          original fetching/search-documents!]
      ;; set! rather than with-redefs, since the worker is asked once the
      ;; feed is fetched; the notes of the episode are ones the feed hasn't
      (set! fetching/search-documents!
            (fn [fetched u]
              (swap! asked conj u)
              (js/Promise.resolve
               (-> (library/with-search-documents fetched u)
                   (assoc-in [:search-documents :documents 1 :fields :description] "Zebras.")
                   :search-documents))))
      (with-redefs [fetching/fetch! (fn [_ _] (js/Promise.resolve {:status 200 :feed fed}))]
        (-> (feeds/subscribe! store url "A Show")
            (.then (fn [_]
                     (is (= [url] @asked) "asks the worker for the documents of the feed")
                     (is (seq (library/search (:library @store) "zebras"))
                         "which the library indexes rather than building them")))
            (.catch #(is false (str "rejected: " %)))
            (.finally #(do (set! fetching/search-documents! original)
                           (done))))))))
