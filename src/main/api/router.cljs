(ns api.router
  (:require
   [api.hal :as hal]
   [api.protocol :as proto]
   [api.url :as url]
   [lobjur.utils.http :as http]
   [clojure.string :as str]
   [lobster.adapter]
   [hackernews.adapter]))

;; Debug flag
(def ^:dynamic *debug* true)

(defn- debug-log [& args]
  "Log debug messages when *debug* is enabled"
  (when *debug*
    (apply js/console.log "[Router Debug]" args)))

;; Route pattern matching

(defn- segment-match
  "Match a single path segment against a pattern segment.
   Returns [match? params] where params is a map of captured values."
  [pattern-seg path-seg]
  (let [result (cond
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
                 [false {}])]
    result))

(defn- match-route
  "Match a path against a route pattern.
   Returns {:matched? bool :params map} or nil if no match."
  [pattern path]
  (debug-log "Trying to match pattern:" pattern "against path:" path)
  (let [pattern-segs (str/split pattern #"/")
        path-segs (str/split path #"/")
        result (when (= (count pattern-segs) (count path-segs))
                 (loop [psegs pattern-segs
                        pathsegs path-segs
                        params {}]
                   (if (empty? psegs)
                     {:matched? true :params params}
                     (let [[matched? seg-params] (segment-match (first psegs) (first pathsegs))]
                       (when matched?
                         (recur (rest psegs) (rest pathsegs) (merge params seg-params)))))))]
    result))

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
  "GET /feeds/{provider} - Source index"
  [{:keys [provider]} _query]
  (if-let [adapter (proto/get-adapter provider)]
    (js/Promise.resolve (proto/source-index adapter))
    (js/Promise.reject (js/Error. (str "Unknown source: " provider)))))

(defn- handle-feed-stories
  "GET /feeds/{provider}/{feed} - Story list"
  [{:keys [provider feed]} query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-feed adapter feed query)
    (js/Promise.reject (js/Error. (str "Unknown source: " provider)))))

(defn- handle-story
  "GET /{provider}/stories/{id} - Single story"
  [{:keys [provider id]} _query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-story adapter id)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

(defn- handle-story-comments
  "GET /{provider}/stories/{id}/comments - Comment tree"
  [{:keys [provider id]} _query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-comments adapter id)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

(defn- handle-user
  "GET /{provider}/users/{username} - User profile"
  [{:keys [provider username]} _query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-user adapter username)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

(defn- handle-user-stories
  "GET /{provider}/users/{username}/stories - User submissions"
  [{:keys [provider username]} query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-user-stories adapter username query)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

(defn- handle-tags
  "GET /feeds/{provider}/tags - Tag list"
  [{:keys [provider]} _query]
  (if-let [adapter (proto/get-adapter provider)]
    (if (proto/supports-tags? adapter)
      (proto/fetch-tags adapter)
      (js/Promise.reject (js/Error. (str "Source " provider " does not support tags"))))
    (js/Promise.reject (js/Error. (str "Unknown source: " provider)))))

(defn- handle-tag-stories
  "GET /{provider}/tags/{tag}/stories - Stories by tag"
  [{:keys [provider tag]} query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-tag-stories adapter tag query)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

(defn- handle-domain-stories
  "GET /{provider}/domains/{domain}/stories - Stories by domain"
  [{:keys [provider domain]} query]
  (if-let [adapter (proto/get-adapter provider)]
    (proto/fetch-domain-stories adapter domain query)
    (js/Promise.reject (js/Error. (str "Unknown provider: " provider)))))

;; Route table

(def routes
  [["/"                                        handle-root]
   ["/feeds"                                   handle-feeds]
   ["/feeds/{provider}"                        handle-feed-source]
   ["/feeds/{provider}/tags"                   handle-tags]
   ["/feeds/{provider}/{feed}"                 handle-feed-stories]
   ["/{provider}/stories/{id}"                 handle-story]
   ["/{provider}/stories/{id}/comments"        handle-story-comments]
   ["/{provider}/users/{username}"             handle-user]
   ["/{provider}/users/{username}/stories"     handle-user-stories]
   ["/{provider}/tags/{tag}/stories"           handle-tag-stories]
   ["/{provider}/domains/{domain}/stories"     handle-domain-stories]])

(defn- find-route
  "Find a matching route for a path."
  [path]
  (let [result (some (fn [[pattern handler]]
                       (when-let [match (match-route pattern path)]
                         {:handler handler
                          :params (:params match)}))
                     routes)]
    (if result
      (debug-log "Found route handler for:" path)
      (debug-log "No route found for:" path))
    result))

;; Request handlers by scheme

(defn- get-in-process
  "Handle in-process API requests using the router."
  [path query-params]
  (debug-log "\n=== In-Process GET ===" path)
  (if-let [{:keys [handler params]} (find-route path)]
    (do
      (debug-log "Calling handler with params:" params "query:" query-params)
      (-> (handler params query-params)
          (.then (fn [result]
                   (debug-log "Response successful for:" path)
                   result))
          (.catch (fn [error]
                    (debug-log "Error occurred:" (.-message error))
                    (js/Promise.reject error)))))
    (do
      (debug-log "Route not found, rejecting promise")
      (js/Promise.reject (js/Error. (str "No route found for: " path))))))

(defn- get-external
  "Handle external HTTP/HTTPS requests."
  [full-url]
  (debug-log "\n=== External GET ===" full-url)
  (-> (http/get full-url)
      (.then (fn [response]
               (debug-log "External response successful for:" full-url)
               ;; Try to parse as JSON, return raw string if fails
               (try
                 (js/JSON.parse response)
                 (catch js/Error _e
                   response))))
      (.catch (fn [error]
                (debug-log "External request failed:" (.-message error))
                (js/Promise.reject error)))))

;; Public API

(defn GET
  "Execute a GET request against the router.
   Supports both in-process and external URLs:
   - Relative paths (e.g., '/feeds/lobsters') -> in-process
   - in-process://api/... -> in-process
   - https://... or http://... -> external HTTP request
   
   Returns a Promise that resolves to a HAL resource (in-process) or response data (external)."
  [request-url]
  (debug-log "\n=== GET Request ===" request-url)
  (let [{:keys [scheme path query-params]} (url/parse-url request-url)]
    (debug-log "Parsed scheme:" scheme "path:" path "query-params:" query-params)
    (case scheme
      :in-process (get-in-process path query-params)
      (:https :http) (get-external path)
      (js/Promise.reject (js/Error. (str "Unsupported URL scheme: " scheme))))))
