(ns hackernews.routes
  (:require
   [clojure.string :as str]
   [api.rest :as r]
   [api.hal :as hal]
   [hackernews.core :as hn]
   [lobjur.utils.common :refer [html->text]]))

;; --- Normalizers (moved from hackernews.adapter) ---

(defn- normalize-hn-story
  "Normalize a HN story to HAL format."
  [story]
  (let [native-id (:id story)
        url (:url story)]
    {:id            native-id
     :provider      "hackernews"
     :title         (:title story)
     :url           url
     :score         (:score story)
     :comment_count (:descendants story)
     :created_at    (when (:time story)
                      (-> (js/Date. (* (:time story) 1000))
                          (.toISOString)))
     :submitter     (:by story)
     :tags          []
     :_links        (hal/story-links "hackernews" native-id
                                     :external-url (not-empty url)
                                     :author (:by story))}))

(defn- placeholder-hn-story
  "Create a placeholder story with just an ID and self-link."
  [hn-id]
  {:id         hn-id
   :provider   "hackernews"
   :_placeholder true
   :_links     {:self (hal/link (str "/hackernews/stories/" hn-id))}})

(defn- normalize-hn-comment
  "Normalize a HN comment to HAL format (shallow, no embedded children)."
  [comment]
  (let [id (:id comment)
        kids (:kids comment)]
    {:id         id
     :provider   "hackernews"
     :text       (html->text (:text comment))
     :created_at (when (:time comment)
                   (-> (js/Date. (* (:time comment) 1000))
                       (.toISOString)))
     :author     (:by comment)
     :score      0
     :_links     (cond-> {:self (hal/link (str "/hackernews/comments/" id))}
                   (:by comment) (assoc :author (hal/link (str "/hackernews/users/" (:by comment))))
                   (seq kids) (assoc :replies (hal/link (str "/hackernews/comments/" id "/replies?kids=" (str/join "," kids)))))}))

(defn- filter-comments
  "Filter out deleted and dead comments."
  [comments]
  (->> comments
       (filter some?)
       (remove :deleted)
       (remove :dead)))

;; --- Handlers ---

(defn- source-index-handler [_request]
  (js/Promise.resolve
   (hal/resource
    {:self   (hal/link "/feeds/hackernews")
     :top    (hal/link "/feeds/hackernews/top")
     :newest (hal/link "/feeds/hackernews/newest")
     :best   (hal/link "/feeds/hackernews/best")}
    {:name        "Hacker News"
     :description "Y Combinator's tech news aggregator"})))

(defn- feed-handler [{:keys [params query]}]
  (let [page (js/parseInt (or (:page query) "1") 10)
        feed (:feed params)
        ids-fn (case feed
                 "top"    hn/top-story-ids
                 "newest" hn/new-story-ids
                 "best"   hn/best-story-ids
                 hn/top-story-ids)
        base-href (str "/feeds/hackernews/" feed)
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

(defn- story-handler [{:keys [params]}]
  (-> (hn/item (:id params))
      (.then (fn [story]
               (let [id (:id story)]
                 (hal/resource
                  (merge (hal/story-links "hackernews" id
                                          :external-url (not-empty (:url story))
                                          :author (:by story))
                         {:feed (hal/link "/feeds/hackernews")})
                  {:id            id
                   :provider      "hackernews"
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

(defn- comment-replies-handler [{:keys [params query]}]
  (let [comment-id (:id params)
        kids-param (:kids query)]
    (if kids-param
      ;; Fast path: kid IDs provided in query, skip parent re-fetch
      (let [kid-ids (mapv #(js/parseInt % 10) (str/split kids-param #","))]
        (-> (js/Promise.all (mapv hn/item kid-ids))
            (.then (fn [kids]
                     (hal/collection
                      (str "/hackernews/comments/" comment-id "/replies") :replies
                      (mapv normalize-hn-comment (filter-comments kids)))))))
      ;; Fallback: fetch parent comment to get kids
      (-> (hn/item comment-id)
          (.then (fn [comment]
                   (let [kid-ids (or (:kids comment) [])]
                     (if (empty? kid-ids)
                       (hal/collection (str "/hackernews/comments/" comment-id "/replies") :replies [])
                       (-> (js/Promise.all (mapv hn/item kid-ids))
                           (.then (fn [kids]
                                    (hal/collection
                                     (str "/hackernews/comments/" comment-id "/replies") :replies
                                     (mapv normalize-hn-comment (filter-comments kids))))))))))))))

(defn- comments-handler [{:keys [params]}]
  (-> (hn/item (:id params))
      (.then (fn [story]
               (let [kid-ids (or (:kids story) [])
                     story-id (:id story)]
                 (if (empty? kid-ids)
                   (hal/resource
                    {:self  (hal/link (str "/hackernews/stories/" story-id "/comments"))
                     :story (hal/link (str "/hackernews/stories/" story-id))}
                    {} {:comments []})
                   (-> (js/Promise.all (mapv hn/item kid-ids))
                       (.then (fn [kids]
                                (hal/resource
                                 {:self  (hal/link (str "/hackernews/stories/" story-id "/comments"))
                                  :story (hal/link (str "/hackernews/stories/" story-id))}
                                 {}
                                 {:comments (mapv normalize-hn-comment (filter-comments kids))}))))))))))
(defn- user-handler [{:keys [params]}]
  (let [username (:username params)]
    (-> (hn/user username)
        (.then (fn [user]
                 (hal/resource
                  (assoc (hal/user-links "hackernews" username)
                         :submitted (hal/link (str "/hackernews/users/" username "/stories")))
                  {:username   (:id user)
                   :provider   "hackernews"
                   :created_at (when (:created user)
                                 (-> (js/Date. (* (:created user) 1000))
                                     (.toISOString)))
                   :karma      (:karma user)
                   :about      (:about user)}))))))

(defn- user-stories-handler [{:keys [params query]}]
  (let [username (:username params)
        page (js/parseInt (or (:page query) "1") 10)
        base-href (str "/hackernews/users/" username "/stories")
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

;; --- Assembled subrouter ---

(def handler
  (r/routes
   (r/mount "/feeds/hackernews"
     (r/routes
       (r/route "/"       source-index-handler)
       (r/route "/{feed}" feed-handler)))

   (r/mount "/hackernews"
     (r/routes
       (r/route "/stories/{id}"              story-handler)
       (r/route "/stories/{id}/comments"     comments-handler)
       (r/route "/comments/{id}/replies"     comment-replies-handler)
       (r/route "/users/{username}"          user-handler)
       (r/route "/users/{username}/stories"  user-stories-handler)))))
