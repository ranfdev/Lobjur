(ns lobster.routes
  (:require
   ["gjs.gi.GLib" :as GLib]
   [api.rest :as r]
   [api.hal :as hal]
   [lobster.core :as lobster]
   [lobjur.utils.common :refer [html->text]]))

;; --- Normalizers (moved from lobster/adapter.cljs) ---

(defn- parse-host [url]
  (when (not-empty url)
    (try
      (.get_host (.parse_relative lobster/base-url url GLib/UriFlags.NONE))
      (catch :default _ nil))))

(defn- build-comment-tree
  "Build a tree structure from flat comments array using parent_comment field."
  [flat-comments]
  (let [;; Group comments by parent_comment
        by-parent (group-by :parent_comment flat-comments)
        ;; Helper to recursively build tree
        build-node (fn build-node [comment]
                     (let [children (get by-parent (:short_id comment))]
                       (if (seq children)
                         (assoc comment :replies (mapv build-node children))
                         comment)))]
    ;; Top-level comments have parent_comment = null
    (mapv build-node (get by-parent nil))))

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
     :_links     (hal/comment-links "lobsters" id
                                    :author (:commenting_user comment)
                                    :replies (seq (:replies comment)))
     :_embedded  (when (seq (:replies comment))
                   {:replies (mapv normalize-lobster-comment (:replies comment))})}))

;; --- Handlers ---

(defn- source-index-handler [_request]
  (js/Promise.resolve
   (hal/resource
    {:self   (hal/link "/feeds/lobsters")
     :hot    (hal/link "/feeds/lobsters/hot")
     :newest (hal/link "/feeds/lobsters/newest")
     :tags   (hal/link "/feeds/lobsters/tags")}
    {:name        "Lobsters"
     :description "Computing-focused link aggregator"})))

(defn- tags-handler [_request]
  (-> (lobster/tags)
      (.then (fn [tags]
               (hal/collection
                "/feeds/lobsters/tags" :tags
                (mapv (fn [tag]
                        (let [tag-name (:tag tag)]
                          {:name        tag-name
                           :description (:description tag)
                           :_links      {:self    (hal/link (str "/lobsters/tags/" tag-name))
                                         :stories (hal/link (str "/lobsters/tags/" tag-name "/stories"))}}))
                      tags))))))

(defn- feed-handler [{:keys [params query]}]
  (let [page (js/parseInt (or (:page query) "1") 10)
        feed (:feed params)
        fetch-fn (case feed
                   "hot"    lobster/hottest
                   "newest" lobster/active
                   lobster/hottest)
        base-href (str "/feeds/lobsters/" feed)
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

(defn- story-handler [{:keys [params]}]
  (-> (lobster/story (:id params))
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

(defn- comments-handler [{:keys [params]}]
  (-> (lobster/story (:id params))
      (.then (fn [story]
               (let [story-id (:short_id story)
                     ;; Build tree from flat comments before normalization
                     comment-tree (build-comment-tree (:comments story))]
                 (hal/resource
                  {:self  (hal/link (str "/lobsters/stories/" story-id "/comments"))
                   :story (hal/link (str "/lobsters/stories/" story-id))}
                  {}
                  {:comments (mapv normalize-lobster-comment comment-tree)}))))))

(defn- user-handler [{:keys [params]}]
  (-> (lobster/user (:username params))
      (.then (fn [user]
               (hal/resource
                (hal/user-links "lobsters" (:username params))
                {:username   (:username user)
                 :provider   "lobsters"
                 :created_at (:created_at user)
                 :karma      (:karma user)
                 :about      (:about user)
                 :avatar_url (:avatar_url user)})))))

(defn- user-stories-handler [{:keys [params query]}]
  (let [username (:username params)
        page (js/parseInt (or (:page query) "1") 10)
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

(defn- tag-stories-handler [{:keys [params query]}]
  (let [tag (:tag params)
        page (js/parseInt (or (:page query) "1") 10)
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

(defn- domain-stories-handler [{:keys [params query]}]
  (let [domain (:domain params)
        page (js/parseInt (or (:page query) "1") 10)
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
                                    :query-params query-params)))))))

;; --- Assembled subrouter ---

(def handler
  (r/routes
   ;; Feed-related routes (under /feeds/lobsters)
   (r/mount "/feeds/lobsters"
            (r/routes
             (r/route "/"      source-index-handler)
             (r/route "/tags"  tags-handler)
             (r/route "/{feed}" feed-handler)))

   ;; Resource routes (under /lobsters)
   (r/mount "/lobsters"
            (r/routes
             (r/route "/stories/{id}"            story-handler)
             (r/route "/stories/{id}/comments"   comments-handler)
             (r/route "/users/{username}"         user-handler)
             (r/route "/users/{username}/stories" user-stories-handler)
             (r/route "/tags/{tag}/stories"       tag-stories-handler)
             (r/route "/domains/{domain}/stories" domain-stories-handler)))))
