(ns hackernews.core
  (:require
   [lobjur.utils.http :as http]
   [lobjur.utils.common :refer [parse-json]]
   ["gjs.gi.GLib" :as GLib]))

(def base-url (GLib/Uri.parse "https://hacker-news.firebaseio.com/v0/" GLib/UriFlags.NONE))

(defn rel [& urls]
  (.to_string (reduce
               (fn [^js base url]
                 (.parse_relative base url GLib/UriFlags.NONE))
               base-url urls)))

;; Low-level API functions

(defn item
  "Fetch a single item (story, comment, job, poll, etc.) by ID."
  [id]
  (.then
   (http/get (rel (str "item/" id ".json")))
   parse-json))

(defn user
  "Fetch a user profile by username."
  [username]
  (.then
   (http/get (rel (str "user/" username ".json")))
   parse-json))

(defn- fetch-story-ids
  "Fetch a list of story IDs from a given endpoint."
  [endpoint]
  (.then
   (http/get (rel (str endpoint ".json")))
   parse-json))

(defn top-story-ids
  "Get IDs of the top 500 stories."
  []
  (fetch-story-ids "topstories"))

(defn new-story-ids
  "Get IDs of the newest 500 stories."
  []
  (fetch-story-ids "newstories"))

(defn best-story-ids
  "Get IDs of the best 500 stories."
  []
  (fetch-story-ids "beststories"))

(defn ask-story-ids
  "Get IDs of the latest Ask HN stories."
  []
  (fetch-story-ids "askstories"))

(defn show-story-ids
  "Get IDs of the latest Show HN stories."
  []
  (fetch-story-ids "showstories"))

(defn job-story-ids
  "Get IDs of the latest job stories."
  []
  (fetch-story-ids "jobstories"))

;; Higher-level functions that fetch full items

(def page-size 30)

(defn- fetch-items
  "Fetch multiple items by their IDs in parallel."
  [ids]
  (js/Promise.all (mapv item ids)))

(defn paginate-ids
  "Get a page of IDs from a list."
  [ids page]
  (let [start (* (dec page) page-size)
        end (+ start page-size)]
    (subvec (vec ids) start (min end (count ids)))))

(defn top-stories
  "Get top stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (top-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

(defn new-stories
  "Get newest stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (new-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

(defn best-stories
  "Get best stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (best-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

(defn ask-stories
  "Get Ask HN stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (ask-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

(defn show-stories
  "Get Show HN stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (show-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

(defn job-stories
  "Get job stories, paginated."
  [& {:keys [page] :or {page 1}}]
  (-> (job-story-ids)
      (.then #(paginate-ids % page))
      (.then fetch-items)))

;; Comment tree fetching

(defn- fetch-comment-tree
  "Recursively fetch a comment and its children."
  [comment-id]
  (-> (item comment-id)
      (.then (fn [comment]
               (if (or (nil? comment) (:deleted comment) (:dead comment))
                 nil
                 (if (seq (:kids comment))
                   (-> (js/Promise.all (mapv fetch-comment-tree (:kids comment)))
                       (.then (fn [children]
                                (assoc comment :children (filterv some? children)))))
                   (assoc comment :children [])))))))

(defn item-with-comments
  "Fetch an item with its full comment tree."
  [item-id]
  (-> (item item-id)
      (.then (fn [story]
               (if (seq (:kids story))
                 (-> (js/Promise.all (mapv fetch-comment-tree (:kids story)))
                     (.then (fn [children]
                              (assoc story :children (filterv some? children)))))
                 (assoc story :children []))))))

;; User submissions

(defn user-stories
  "Get stories submitted by a user, paginated."
  [username & {:keys [page] :or {page 1}}]
  (-> (user username)
      (.then (fn [u]
               (if-let [submitted (:submitted u)]
                 (-> (fetch-items (paginate-ids submitted page))
                     (.then (fn [items]
                              (filterv #(= (:type %) "story") items))))
                 [])))))
