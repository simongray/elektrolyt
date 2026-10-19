(ns dk.simongray.podcast-app.linked
  "The shows that a show links to, which its Show page shows: the shows of
  its podroll, which its creator recommends, and the other shows of its
  publisher, once the publisher's feed lists this show too, as the podcast
  namespace asks.

  The state keeps them under the guid of the show in :linked, as a map of
  the :status of their fetch, :fetching, :done or :failed, the :podroll,
  as cards, and the :publisher, a map of its :title and the cards of its
  :shows. A card is what the page shows of a show: its :guid, :title, the
  :url of its feed, and its :artwork and :artwork-sizes."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]))

(defn links?
  "Whether the parsed `feed` links to other shows, with a podroll or a
  publisher."
  [feed]
  (boolean (or (seq (resolve/podroll feed)) (resolve/publisher feed))))

(defn card
  "The card of the remote `item` of a podroll or a publisher feed, with
  the start of its feed as its :show when that was fetched, or nil when it
  names no show."
  [{:keys [feed-guid feed-url title show] :as item}]
  (let [url (or feed-url (some-> show resolve/self-url))
        id  (or feed-guid (when (and show url) (guid/feed-guid show url)))]
    (when id
      (into {}
            (remove (comp nil? val))
            {:guid          id
             :title         (or (some-> show resolve/title) title)
             :url           url
             :artwork       (some-> show resolve/artwork)
             :artwork-sizes (some-> show resolve/artwork-sizes not-empty)}))))

(defn kept
  "What the page shows of the shows that app.show/fetch-linked-shows! gave,
  `fetched`, for the show `guid`: the cards of the podroll, and of the
  publisher's other shows when its feed confirms it, which is what the
  cache keeps of them."
  [guid {:keys [podroll publisher]}]
  (let [others (when (:confirmed? publisher)
                 (->> (:shows publisher)
                      (keep card)
                      (remove #(= guid (:guid %)))
                      vec))]
    (cond-> {:podroll (into [] (keep card) podroll)}
      (seq others) (assoc :publisher {:title (some-> (:feed publisher) resolve/title)
                                      :shows others}))))
