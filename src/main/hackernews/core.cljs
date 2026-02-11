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

;; Higher-level functions that fetch full items

(def page-size 30)

(defn paginate-ids
  "Get a page of IDs from a list."
  [ids page]
  (let [start (* (dec page) page-size)
        end (+ start page-size)]
    (subvec (vec ids) start (min end (count ids)))))

;; Search via Algolia

(defn search
  "Search HN stories via Algolia API."
  [query & {:keys [page] :or {page 0}}]
  (.then
   (http/get "https://hn.algolia.com/api/v1/search"
             {:params {:query query :page page :hitsPerPage 30 :tags "story"}})
   parse-json))