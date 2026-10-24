(ns dk.simongray.elektrolyt.show.linked
  "The shows that a show links to: those its creator recommends in its
  podroll, and its publisher's other shows, once the publisher's feed
  lists this show too, as the podcast namespace asks.

  Each show's entry in :linked has the :status of the fetch, :fetching,
  :done or :failed, the :podroll as cards, and the :publisher, a map of
  its :title and the cards of its :shows."
  (:require [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]))

(defn links?
  "Whether the parsed `feed` links to other shows, with a podroll or a
  publisher."
  [feed]
  (boolean (or (seq (resolve/podroll feed)) (resolve/publisher feed))))

(defn card
  "The card of the remote `item` in a podroll or a publisher feed, or nil
  when it names no show. A card has the show's guid, title, feed URL and
  artwork, from the start of its feed in :show where it was fetched."
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

(defn failed?
  "Whether a part of the linked shows `fetched` failed: the feed of a show
  in the podroll, or the publisher's feed."
  [{:keys [podroll publisher]}]
  (boolean (or (some :error podroll) (:error publisher))))

(defn kept
  "What the page shows of the linked shows `fetched` for the show that goes
  by the set of guids `own`: the podroll's cards, and the publisher's
  title and the cards of its other shows when its feed confirms this one."
  [own {:keys [podroll publisher]}]
  (let [others (when (:confirmed? publisher)
                 (->> (:shows publisher)
                      (keep card)
                      (remove (comp own :guid))
                      vec))]
    (cond-> {:podroll (into [] (keep card) podroll)}
      (seq others) (assoc :publisher {:title (some-> (:feed publisher) resolve/title)
                                      :shows others}))))
