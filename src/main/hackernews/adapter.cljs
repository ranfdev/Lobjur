(ns hackernews.adapter
  (:require
   [api.protocol :as proto]
   [api.hal :as hal]
   [hackernews.core :as hn]
   [lobjur.utils.common :refer [html->text]]))

(defn- normalize-hn-story
  "Normalize a HN story to HAL format."
  [story]
  (let [id (proto/make-story-id :hn (:id story))]
    {:id            id
     :title         (:title story)
     :url           (:url story)
     :score         (:score story)
     :comment_count (:descendants story)
     :created_at    (when (:time story)
                      (-> (js/Date. (* (:time story) 1000))
                          (.toISOString)))
     :submitter     (:by story)
     :tags          []
     :_links        (hal/story-links id
                                     :external-url (not-empty (:url story))
                                     :author (:by story))}))

(defn- normalize-hn-comment
  "Normalize a HN comment to HAL format."
  [comment]
  (let [id (str "hn-c-" (:id comment))]
    {:id         id
     :text       (html->text (:text comment))
     :created_at (when (:time comment)
                   (-> (js/Date. (* (:time comment) 1000))
                       (.toISOString)))
     :author     (:by comment)
     :score      0
     :_links     (hal/comment-links id :author (:by comment))
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
          fetch-fn (case feed-type
                     "top"    hn/top-stories
                     "newest" hn/new-stories
                     "best"   hn/best-stories
                     hn/top-stories)
          base-href (str "/feeds/hn/" feed-type)
          query-params (dissoc query :page)]
      (-> (fetch-fn :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-hn-story stories))
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 30)
                                      :has-prev (> page 1)
                                      :query-params query-params)))))))
  
  (fetch-story [_ native-id]
    (-> (hn/item native-id)
        (.then (fn [story]
                 (let [id (proto/make-story-id :hn (:id story))]
                   (hal/resource
                    (merge (hal/story-links id
                                            :external-url (not-empty (:url story))
                                            :author (:by story))
                           {:feed (hal/link "/feeds/hn")})
                    {:id            id
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
                 (let [story-id (proto/make-story-id :hn (:id story))]
                   (hal/resource
                    {:self  (hal/link (str "/stories/" story-id "/comments"))
                     :story (hal/link (str "/stories/" story-id))}
                    {}
                    {:comments (mapv normalize-hn-comment (:children story))}))))))
  
  (fetch-user [_ username]
    (-> (hn/user username)
        (.then (fn [user]
                 (hal/resource
                  (assoc (hal/user-links username)
                         :submitted (hal/link (str "/users/" username "/stories?source=hn")))
                  {:username   (:id user)
                   :created_at (when (:created user)
                                 (-> (js/Date. (* (:created user) 1000))
                                     (.toISOString)))
                   :karma      (:karma user)
                   :about      (:about user)})))))
  
  (fetch-user-stories [_ username query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/users/" username "/stories")
          query-params (dissoc query :page)]
      (-> (hn/user-stories username :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-hn-story stories))
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 30)
                                      :has-prev (> page 1)
                                      :query-params query-params)))))))
  
  (fetch-tags [_]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-tag-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-domain-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support domain filtering"))))

(proto/register-adapter! "hn" (->HNAdapter))
