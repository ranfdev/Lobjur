(ns lobster.adapter
  (:require
   ["gjs.gi.GLib" :as GLib]
   [api.protocol :as proto]
   [api.hal :as hal]
   [lobster.core :as lobster]
   [lobjur.utils.common :refer [html->text]]))

(defn- parse-host [url]
  (when (not-empty url)
    (try
      (.get_host (.parse_relative lobster/base-url url GLib/UriFlags.NONE))
      (catch :default _ nil))))

(defn- normalize-lobster-story
  "Normalize a Lobsters story to HAL format."
  [story]
  (let [native-id (:short_id story)
        url (:url story)]
    {:id            native-id
     :provider      "lobsters"
     :title         (:title story)
     :url           url
     :score         (:score story)
     :comment_count (:comment_count story)
     :created_at    (:created_at story)
     :submitter     (:submitter_user story)
     :tags          (:tags story)
     :_links        (hal/story-links "lobsters" native-id
                                     :external-url (not-empty url)
                                     :author (:submitter_user story)
                                     :tags (:tags story)
                                     :domain (parse-host url))}))

(defn- normalize-lobster-comment
  "Normalize a Lobsters comment to HAL format."
  [comment]
  (let [id (:short_id comment)]
    {:id         id
     :provider   "lobsters"
     :text       (html->text (:comment comment))
     :created_at (:created_at comment)
     :author     (:commenting_user comment)
     :score      (:score comment)
     :_links     (hal/comment-links "lobsters" id :author (:commenting_user comment))
     :_embedded  (when (seq (:replies comment))
                   {:replies (mapv normalize-lobster-comment (:replies comment))})}))

;; Lobsters Adapter

(defrecord LobstersAdapter []
  proto/BackendAdapter
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
          base-href (str "/feeds/lobsters/" feed-type)
          query-params (dissoc query :page)]
      (-> (fetch-fn :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 20)
                                      :has-prev (> page 1)
                                      :query-params query-params)))))))
  
  (fetch-story [_ native-id]
    (-> (lobster/story native-id)
        (.then (fn [story]
                 (let [id (:short_id story)]
                   (hal/resource
                    (merge (hal/story-links "lobsters" id
                                            :external-url (not-empty (:url story))
                                            :author (:submitter_user story)
                                            :tags (:tags story)
                                            :domain (parse-host (:url story)))
                           {:feed (hal/link "/feeds/lobsters")})
                    {:id            id
                     :provider      "lobsters"
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
                 (let [story-id (:short_id story)]
                   (hal/resource
                    {:self  (hal/link (str "/lobsters/stories/" story-id "/comments"))
                     :story (hal/link (str "/lobsters/stories/" story-id))}
                    {}
                    {:comments (mapv normalize-lobster-comment (:comments story))}))))))
  
  (fetch-user [_ username]
    (-> (lobster/user username)
        (.then (fn [user]
                 (hal/resource
                  (hal/user-links "lobsters" username)
                  {:username   (:username user)
                   :provider   "lobsters"
                   :created_at (:created_at user)
                   :karma      (:karma user)
                   :about      (:about user)
                   :avatar_url (:avatar_url user)})))))
  
  (fetch-user-stories [_ username query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/lobsters/users/" username "/stories")
          query-params (dissoc query :page)]
      (-> (lobster/user-stories-newest username :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 20)
                                      :has-prev (> page 1)
                                      :query-params query-params)))))))
  
  (fetch-tags [_]
    (js/Promise.resolve
     (hal/collection
      "/feeds/lobsters/tags" :tags
      [{:name "programming" :_links {:self (hal/link "/lobsters/tags/programming")
                                     :stories (hal/link "/lobsters/tags/programming/stories")}}
       {:name "web" :_links {:self (hal/link "/lobsters/tags/web")
                             :stories (hal/link "/lobsters/tags/web/stories")}}])))
  
  (fetch-tag-stories [_ tag query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/lobsters/tags/" tag "/stories")
          query-params (dissoc query :page)]
      (-> (lobster/tagged tag :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories)
                        :links {:tag (hal/link (str "/lobsters/tags/" tag))})
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 20)
                                      :has-prev (> page 1)
                                      :query-params query-params)))))))
  
  (fetch-domain-stories [_ domain query]
    (let [page (js/parseInt (or (:page query) "1") 10)
          base-href (str "/lobsters/domains/" domain "/stories")
          query-params (dissoc query :page)]
      (-> (lobster/domain-stories domain :page page)
          (.then (fn [stories]
                   (-> (hal/collection
                        base-href :stories
                        (mapv normalize-lobster-story stories))
                       (hal/paginated base-href page 
                                      :has-next (>= (count stories) 20)
                                      :has-prev (> page 1)
                                      :query-params query-params))))))))

(proto/register-adapter! "lobsters" (->LobstersAdapter))
