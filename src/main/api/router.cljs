(ns api.router
  (:require
   [clojure.string :as str]
   [api.rest :as r]
   [api.hal :as hal]
   [api.url :as url]
   [api.server :as s]
   [api.debug :as debug]
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

;; --- Server (handler + middleware) ---

(def server
  "The main server: routes external URLs to HTTP, in-process URLs to the app handler.
   Wrapped with history recording for REPL debugging."
  (-> (fn [request]
        (let [path (:path request)]
          (if (or (str/starts-with? path "https://")
                  (str/starts-with? path "http://"))
            (get-external path)
            (r/dispatch app request))))
      (s/with-history debug/*history*)))

;; --- Public API ---

(defn GET
  "Execute a GET request. Backward-compatible API.
   Supports both in-process and external URLs."
  [request-url]
  (s/GET server request-url))
