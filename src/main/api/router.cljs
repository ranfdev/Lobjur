(ns api.router
  (:require
   [api.rest :as r]
   [api.hal :as hal]
   [api.url :as url]
   [lobjur.utils.http :as http]
   [lobster.routes :as lobster]
   [hackernews.routes :as hn]))

;; --- Top-level route handlers ---

(defn- root-handler [_request]
  (js/Promise.resolve
   (hal/resource
    {:self   (hal/link "/")
     :feeds  (hal/link "/feeds")}
    {:title   "Lobjur API"
     :version "1.0"})))

(defn- feeds-handler [_request]
  (js/Promise.resolve
   (hal/collection
    "/feeds" :feeds
    [{:name "Lobsters"
      :_links {:self   (hal/link "/feeds/lobsters")
               :hot    (hal/link "/feeds/lobsters/hot")
               :newest (hal/link "/feeds/lobsters/newest")
               :tags   (hal/link "/feeds/lobsters/tags")}}
     {:name "Hacker News"
      :_links {:self   (hal/link "/feeds/hackernews")
               :top    (hal/link "/feeds/hackernews/top")
               :newest (hal/link "/feeds/hackernews/newest")
               :best   (hal/link "/feeds/hackernews/best")}}])))

;; --- Composed application handler ---

(def app
  (r/routes
   (r/route "/"      root-handler)
   (r/route "/feeds" feeds-handler)
   lobster/handler
   hn/handler))

;; --- External URL handler ---

(defn- get-external
  "Handle external HTTP/HTTPS requests."
  [full-url]
  (-> (http/get full-url)
      (.then (fn [response]
               (try
                 (js/JSON.parse response)
                 (catch js/Error _e
                   response))))))

;; --- Public API ---

(defn GET
  "Execute a GET request against the router.
   Supports both in-process and external URLs:
   - Relative paths (e.g., '/feeds/lobsters') -> in-process
   - in-process://api/... -> in-process
   - https://... or http://... -> external HTTP request
   
   Returns a Promise that resolves to a HAL resource (in-process) or response data (external)."
  [request-url]
  (let [{:keys [scheme path query-params]} (url/parse-url request-url)]
    (case scheme
      :in-process (r/dispatch app path query-params)
      (:https :http) (get-external path)
      (js/Promise.reject (js/Error. (str "Unsupported URL scheme: " scheme))))))
