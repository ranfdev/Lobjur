(ns hackernews.adapter
  (:require
   ["gjs.gi.GLib" :as GLib]
   [api.protocol :as proto]
   [api.hal :as hal]
   [hackernews.core :as hn]
   [lobjur.utils.common :refer [html->text]]))

(def ^:private dummy-base (GLib/Uri.parse "https://example.com/" GLib/UriFlags.NONE))

(defn- parse-host [url]
  (when (not-empty url)
    (try
      (.get_host (.parse_relative dummy-base url GLib/UriFlags.NONE))
      (catch :default _ nil))))

(defn- normalize-hn-story
  "Normalize a HN story to HAL format."
  [story]
  (let [native-id (:id story)
        url (:url story)]
    {:id            native-id
     :provider      "hn"
     :title         (:title story)
     :url           url
     :score         (:score story)
     :comment_count (:descendants story)
     :created_at    (when (:time story)
                      (-> (js/Date. (* (:time story) 1000))
                          (.toISOString)))
     :submitter     (:by story)
     :tags          []
     :_links        (hal/story-links "hn" native-id
                                     :external-url (not-empty url)
                                     :author (:by story)
                                     :domain (parse-host url))}))

(defn- placeholder-hn-story
  "Create a placeholder story with just an ID and self-link."
  [hn-id]
  {:id         hn-id
   :provider   "hn"
   :_placeholder true
   :_links     {:self (hal/link (str "/hn/stories/" hn-id))}})

(defn- normalize-hn-comment
  "Normalize a HN comment to HAL format."
  [comment]
  (let [id (:id comment)]
    {:id         id
     :provider   "hn"
     :text       (html->text (:text comment))
     :created_at (when (:time comment)
                   (-> (js/Date. (* (:time comment) 1000))
                       (.toISOString)))
     :author     (:by comment)
     :score      0
     :_links     (hal/comment-links "hn" id :author (:by comment))
     :_embedded  (when (seq (:children comment))
                   {:replies (mapv normalize-hn-comment (:children comment))})}))

;; HackerNews Adapter

(defrecord HNAdapter []
  proto/BackendAdapter
  (source-name [_] "Hacker News")
  (source-id [_] "hn")
  
  (source-index [_]
    (hal/resource
     {:self   (hal/link "/feeds/hn")
      :top    (hal/link "/feeds/hn/top")
      :newest (hal/link "/feeds/hn/newest")
      :best   (hal/link "/feeds/hn/best")}
     {:name        "Hacker News"
      :description "Y Combinator's tech news aggregator"}))
  
  (supports-tags? [_] false)
  
  (fetch-feed [_ feed-type query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          ids-fn (case feed-type
                   "top"    hn/top-story-ids
                   "newest" hn/new-story-ids
                   "best"   hn/best-story-ids
                   hn/top-story-ids)
          base-href (str "/feeds/hn/" feed-type)
          query-params (dissoc query :page)]
      (-> (ids-fn)
          (.then (fn [ids]
                   (let [page-ids (hn/paginate-ids ids page)]
                     (-> (hal/collection
                          base-href :stories
                          (mapv placeholder-hn-story page-ids))
                         (hal/paginated base-href page
                                        :has-next (>= (count page-ids) hn/page-size)
                                        :has-prev (> page 1)
                                        :query-params query-params))))))))
  
  (fetch-story [_ native-id]
    (-> (hn/item native-id)
        (.then (fn [story]
                 (let [id (:id story)]
                   (hal/resource
                    (merge (hal/story-links "hn" id
                                            :external-url (not-empty (:url story))
                                            :author (:by story)
                                            :domain (parse-host (:url story)))
                           {:feed (hal/link "/feeds/hn")})
                    {:id            id
                     :provider      "hn"
                     :title         (:title story)
                     :url           (:url story)
                     :score         (:score story)
                     :comment_count (:descendants story)
                     :text          (:text story)
                     :created_at    (when (:time story)
                                      (-> (js/Date. (* (:time story) 1000))
                                          (.toISOString)))
                     :submitter     (:by story)
                     :tags          []}))))))
  
  (fetch-comments [_ native-id]
    (-> (hn/item-with-comments native-id)
        (.then (fn [story]
                 (let [story-id (:id story)]
                   (hal/resource
                    {:self  (hal/link (str "/hn/stories/" story-id "/comments"))
                     :story (hal/link (str "/hn/stories/" story-id))}
                    {}
                    {:comments (mapv normalize-hn-comment (:children story))}))))))
  
  (fetch-user [_ username]
    (-> (hn/user username)
        (.then (fn [user]
                 (hal/resource
                  (assoc (hal/user-links "hn" username)
                         :submitted (hal/link (str "/hn/users/" username "/stories")))
                  {:username   (:id user)
                   :provider   "hn"
                   :created_at (when (:created user)
                                 (-> (js/Date. (* (:created user) 1000))
                                     (.toISOString)))
                   :karma      (:karma user)
                   :about      (:about user)})))))
  
  (fetch-user-stories [_ username query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/hn/users/" username "/stories")
          query-params (dissoc query :page)]
      (-> (hn/user username)
          (.then (fn [u]
                   (let [submitted (or (:submitted u) [])
                         page-ids (hn/paginate-ids submitted page)]
                     (-> (hal/collection
                          base-href :stories
                          (mapv placeholder-hn-story page-ids))
                         (hal/paginated base-href page
                                        :has-next (>= (count page-ids) hn/page-size)
                                        :has-prev (> page 1)
                                        :query-params query-params))))))))
  
  (fetch-tags [_]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-tag-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-domain-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support domain filtering"))))

(proto/register-adapter! "hn" (->HNAdapter))
