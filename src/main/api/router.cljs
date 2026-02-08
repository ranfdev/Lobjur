(ns api.router
  (:require
   [api.hal :as hal]
   [api.adapters :as adapters]
   [clojure.string :as str]))

;; Route pattern matching

(defn- segment-match
  "Match a single path segment against a pattern segment.
   Returns [match? params] where params is a map of captured values."
  [pattern-seg path-seg]
  (cond
    ;; Exact match
    (= pattern-seg path-seg)
    [true {}]

    ;; Parameter capture {:param}
    (and (string? pattern-seg)
         (str/starts-with? pattern-seg "{")
         (str/ends-with? pattern-seg "}"))
    (let [param-name (keyword (subs pattern-seg 1 (dec (count pattern-seg))))]
      [true {param-name path-seg}])

    :else
    [false {}]))

(defn- match-route
  "Match a path against a route pattern.
   Returns {:matched? bool :params map} or nil if no match."
  [pattern path]
  (let [pattern-segs (str/split pattern #"/")
        path-segs (str/split path #"/")]
    (when (= (count pattern-segs) (count path-segs))
      (loop [psegs pattern-segs
             pathsegs path-segs
             params {}]
        (if (empty? psegs)
          {:matched? true :params params}
          (let [[matched? seg-params] (segment-match (first psegs) (first pathsegs))]
            (when matched?
              (recur (rest psegs) (rest pathsegs) (merge params seg-params)))))))))

(defn- parse-query-params
  "Parse query string into a map."
  [query-string]
  (when (and query-string (not (str/blank? query-string)))
    (into {}
          (for [pair (str/split query-string #"&")
                :let [[k v] (str/split pair #"=" 2)]
                :when k]
            [(keyword k) (or v true)]))))

(defn- parse-url
  "Parse a URL into {:path :query-params}."
  [url]
  (let [[path query] (str/split url #"\?" 2)
        normalized (if (and (> (count path) 1) (str/ends-with? path "/"))
                     (subs path 0 (dec (count path)))
                     path)]
    {:path normalized
     :query-params (parse-query-params query)}))

;; Route handlers

(defn- handle-root
  "GET / - Entry point"
  [_params _query]
  (js/Promise.resolve
   (hal/resource
    {:self   (hal/link "/")
     :feeds  (hal/link "/feeds")}
    {:title   "Lobjur API"
     :version "1.0"})))

(defn- handle-feeds
  "GET /feeds - Available sources"
  [_params _query]
  (js/Promise.resolve
   (hal/collection
    "/feeds" :feeds
    [{:name "Lobsters"
      :_links {:self   (hal/link "/feeds/lobsters")
               :hot    (hal/link "/feeds/lobsters/hot")
               :newest (hal/link "/feeds/lobsters/newest")
               :tags   (hal/link "/feeds/lobsters/tags")}}
     {:name "Hacker News"
      :_links {:self   (hal/link "/feeds/hn")
               :top    (hal/link "/feeds/hn/top")
               :newest (hal/link "/feeds/hn/newest")
               :best   (hal/link "/feeds/hn/best")}}])))

(defn- handle-feed-source
  "GET /feeds/{source} - Source index"
  [{:keys [source]} _query]
  (if-let [adapter (adapters/get-adapter source)]
    (js/Promise.resolve (adapters/source-index adapter))
    (js/Promise.reject (js/Error. (str "Unknown source: " source)))))

(defn- handle-feed-stories
  "GET /feeds/{source}/{feed} - Story list"
  [{:keys [source feed]} query]
  (if-let [adapter (adapters/get-adapter source)]
    (adapters/fetch-feed adapter feed query)
    (js/Promise.reject (js/Error. (str "Unknown source: " source)))))

(defn- handle-story
  "GET /stories/{id} - Single story"
  [{:keys [id]} _query]
  (if-let [{:keys [backend native-id]} (adapters/lookup-id id)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-story adapter native-id)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))
    (js/Promise.reject (js/Error. (str "Unknown story: " id)))))

(defn- handle-story-comments
  "GET /stories/{id}/comments - Comment tree"
  [{:keys [id]} _query]
  (if-let [{:keys [backend native-id]} (adapters/lookup-id id)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-comments adapter native-id)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))
    (js/Promise.reject (js/Error. (str "Unknown story: " id)))))

(defn- handle-user
  "GET /users/{username} - User profile"
  [{:keys [username]} query]
  ;; Try to determine which backend from the query or stored mapping
  (let [backend (or (keyword (:source query)) :lobsters)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-user adapter username)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))))

(defn- handle-user-stories
  "GET /users/{username}/stories - User submissions"
  [{:keys [username]} query]
  (let [backend (or (keyword (:source query)) :lobsters)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-user-stories adapter username query)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))))

(defn- handle-tags
  "GET /feeds/{source}/tags - Tag list"
  [{:keys [source]} _query]
  (if-let [adapter (adapters/get-adapter source)]
    (if (adapters/supports-tags? adapter)
      (adapters/fetch-tags adapter)
      (js/Promise.reject (js/Error. (str "Source " source " does not support tags"))))
    (js/Promise.reject (js/Error. (str "Unknown source: " source)))))

(defn- handle-tag-stories
  "GET /tags/{tag}/stories - Stories by tag"
  [{:keys [tag]} query]
  (let [backend (or (keyword (:source query)) :lobsters)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-tag-stories adapter tag query)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))))

(defn- handle-domain-stories
  "GET /domains/{domain}/stories - Stories by domain"
  [{:keys [domain]} query]
  (let [backend (or (keyword (:source query)) :lobsters)]
    (if-let [adapter (adapters/get-adapter backend)]
      (adapters/fetch-domain-stories adapter domain query)
      (js/Promise.reject (js/Error. (str "Unknown backend: " backend))))))

;; Route table

(def routes
  [["/"                           handle-root]
   ["/feeds"                      handle-feeds]
   ["/feeds/{source}"             handle-feed-source]
   ["/feeds/{source}/tags"        handle-tags]
   ["/feeds/{source}/{feed}"      handle-feed-stories]
   ["/stories/{id}"               handle-story]
   ["/stories/{id}/comments"      handle-story-comments]
   ["/users/{username}"           handle-user]
   ["/users/{username}/stories"   handle-user-stories]
   ["/tags/{tag}/stories"         handle-tag-stories]
   ["/domains/{domain}/stories"   handle-domain-stories]])

(defn- find-route
  "Find a matching route for a path."
  [path]
  (some (fn [[pattern handler]]
          (when-let [match (match-route pattern path)]
            {:handler handler
             :params (:params match)}))
        routes))

;; Public API

(defn GET
  "Execute a GET request against the router.
   Returns a Promise that resolves to a HAL resource."
  [url]
  (let [{:keys [path query-params]} (parse-url url)]
    (if-let [{:keys [handler params]} (find-route path)]
      (handler params query-params)
      (js/Promise.reject (js/Error. (str "No route found for: " path))))))
