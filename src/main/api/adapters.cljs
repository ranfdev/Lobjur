(ns api.adapters
  (:require
   [api.hal :as hal]
   [lobster.core :as lobster]
   [hackernews.core :as hn]))

;; ID Registry - maps public IDs to backend-specific data
(defonce id-registry (atom {}))

(defn register-id!
  "Register a public ID mapping to a backend."
  [public-id backend native-id]
  (swap! id-registry assoc public-id {:backend backend :native-id native-id})
  public-id)

(defn lookup-id
  "Look up backend info for a public ID."
  [public-id]
  (get @id-registry public-id))

(defn make-story-id
  "Create a unique story ID and register it."
  [backend native-id]
  (let [public-id (str (name backend) "-" native-id)]
    (register-id! public-id backend native-id)
    public-id))

;; Protocol for backend adapters

(defprotocol BackendAdapter
  (source-name [this])
  (source-id [this])
  (source-index [this])
  (supports-tags? [this])
  (fetch-feed [this feed-type query])
  (fetch-story [this native-id])
  (fetch-comments [this native-id])
  (fetch-user [this username])
  (fetch-user-stories [this username query])
  (fetch-tags [this])
  (fetch-tag-stories [this tag query])
  (fetch-domain-stories [this domain query]))

;; Story normalization helpers

(defn- normalize-lobster-story
  "Normalize a Lobsters story to HAL format."
  [story]
  (let [id (make-story-id :lobsters (:short_id story))]
    {:id            id
     :title         (:title story)
     :url           (:url story)
     :score         (:score story)
     :comment_count (:comment_count story)
     :created_at    (:created_at story)
     :submitter     (:submitter_user story)
     :tags          (:tags story)
     :_links        (hal/story-links id
                                     :external-url (:url story)
                                     :author (:submitter_user story))}))

(defn- normalize-lobster-comment
  "Normalize a Lobsters comment to HAL format."
  [comment]
  (let [id (str "lobsters-c-" (:short_id comment))]
    {:id         id
     :text       (:comment comment)
     :created_at (:created_at comment)
     :author     (:commenting_user comment)
     :score      (:score comment)
     :_links     (hal/comment-links id :author (:commenting_user comment))
     :_embedded  (when (seq (:replies comment))
                   {:replies (mapv normalize-lobster-comment (:replies comment))})}))

(defn- normalize-hn-story
  "Normalize a HN story to HAL format."
  [story]
  (let [id (make-story-id :hn (:id story))]
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
                                     :external-url (:url story)
                                     :author (:by story))}))

(defn- normalize-hn-comment
  "Normalize a HN comment to HAL format."
  [comment]
  (let [id (str "hn-c-" (:id comment))]
    {:id         id
     :text       (:text comment)
     :created_at (when (:time comment)
                   (-> (js/Date. (* (:time comment) 1000))
                       (.toISOString)))
     :author     (:by comment)
     :score      0
     :_links     (hal/comment-links id :author (:by comment))
     :_embedded  (when (seq (:children comment))
                   {:replies (mapv normalize-hn-comment (:children comment))})}))

;; Lobsters Adapter

(defrecord LobstersAdapter []
  BackendAdapter
  (source-name [_] "Lobsters")
  (source-id [_] "lobsters")
  
  (source-index [_]
    (hal/resource
     {:self   (hal/link "/feeds/lobsters")
      :hot    (hal/link "/feeds/lobsters/hot")
      :newest (hal/link "/feeds/lobsters/newest")
      :tags   (hal/link "/feeds/lobsters/tags")}
     {:name        "Lobsters"
      :description "Computing-focused link aggregator"}))
  
  (supports-tags? [_] true)
  
  (fetch-feed [_ feed-type query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          fetch-fn (case feed-type
                     "hot"    lobster/hottest
                     "newest" lobster/active
                     lobster/hottest)
          base-href (str "/feeds/lobsters/" feed-type)]
      (-> (fetch-fn :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page :has-next (>= (count stories) 20))))))))
  
  (fetch-story [_ native-id]
    (-> (lobster/story native-id)
        (.then (fn [story]
                 (let [id (make-story-id :lobsters (:short_id story))]
                   (hal/resource
                    (merge (hal/story-links id
                                            :external-url (:url story)
                                            :author (:submitter_user story))
                           {:feed (hal/link "/feeds/lobsters")})
                    {:id            id
                     :title         (:title story)
                     :url           (:url story)
                     :score         (:score story)
                     :comment_count (:comment_count story)
                     :text          (:description story)
                     :created_at    (:created_at story)
                     :submitter     (:submitter_user story)
                     :tags          (:tags story)}))))))
  
  (fetch-comments [_ native-id]
    (-> (lobster/story native-id)
        (.then (fn [story]
                 (let [story-id (make-story-id :lobsters (:short_id story))]
                   (hal/resource
                    {:self  (hal/link (str "/stories/" story-id "/comments"))
                     :story (hal/link (str "/stories/" story-id))}
                    {}
                    {:comments (mapv normalize-lobster-comment (:comments story))}))))))
  
  (fetch-user [_ username]
    (-> (lobster/user username)
        (.then (fn [user]
                 (hal/resource
                  (hal/user-links username)
                  {:username   (:username user)
                   :created_at (:created_at user)
                   :karma      (:karma user)
                   :about      (:about user)
                   :avatar_url (:avatar_url user)})))))
  
  (fetch-user-stories [_ username query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/users/" username "/stories")]
      (-> (lobster/user-stories-newest username :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page :has-next (>= (count stories) 20))))))))
  
  (fetch-tags [_]
    (js/Promise.resolve
     (hal/collection
      "/feeds/lobsters/tags" :tags
      [{:name "programming" :_links {:self (hal/link "/tags/programming")
                                     :stories (hal/link "/tags/programming/stories?source=lobsters")}}
       {:name "web" :_links {:self (hal/link "/tags/web")
                             :stories (hal/link "/tags/web/stories?source=lobsters")}}])))
  
  (fetch-tag-stories [_ tag query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/tags/" tag "/stories")]
      (-> (lobster/tagged tag :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories)
                        :links {:tag (hal/link (str "/tags/" tag))})
                       (hal/paginated base-href page :has-next (>= (count stories) 20))))))))
  
  (fetch-domain-stories [_ domain query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/domains/" domain "/stories")]
      (-> (lobster/domain-stories domain :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page :has-next (>= (count stories) 20)))))))))

;; HackerNews Adapter

(defrecord HNAdapter []
  BackendAdapter
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
          base-href (str "/feeds/hn/" feed-type)]
      (-> (fetch-fn :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-hn-story stories))
                       (hal/paginated base-href page :has-next (>= (count stories) 30))))))))
  
  (fetch-story [_ native-id]
    (-> (hn/item native-id)
        (.then (fn [story]
                 (let [id (make-story-id :hn (:id story))]
                   (hal/resource
                    (merge (hal/story-links id
                                            :external-url (:url story)
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
                 (let [story-id (make-story-id :hn (:id story))]
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
          base-href (str "/users/" username "/stories")]
      (-> (hn/user-stories username :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-hn-story stories))
                       (hal/paginated base-href page :has-next (>= (count stories) 30))))))))
  
  (fetch-tags [_]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-tag-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support tags")))
  
  (fetch-domain-stories [_ _ _]
    (js/Promise.reject (js/Error. "HackerNews does not support domain filtering"))))

;; Adapter Registry

(def adapters
  {"lobsters" (->LobstersAdapter)
   "hn"       (->HNAdapter)})

(defn get-adapter
  "Get an adapter by source name/id."
  [source]
  (get adapters (name source)))
