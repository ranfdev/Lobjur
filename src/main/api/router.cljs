(ns api.router
  (:require
   [clojure.string :as str]
   [api.rest :as r]
   [api.hal :as hal]
   [api.url :as url]
   [api.server :as s]
   [api.debug :as debug]
   [api.sources :as sources]
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
    (mapv (fn [src]
            {:name (:name src)
             :_links (merge
                      {:self (hal/link (str "/feeds/" (:id src)))}
                      (into {}
                            (map (fn [f] [(:rel f) (hal/link (str "/feeds/" (:id src) "/" (:id f)))]))
                            (:feeds src))
                      (into {}
                            (map (fn [[k v]] [k (hal/link v)]))
                            (:extra-links src)))})
          sources/sources))))

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
   Wrapped with history recording and an in-memory cache for REPL debugging."
  (-> (fn [request]
        (let [path (:path request)]
          (if (or (str/starts-with? path "https://")
                  (str/starts-with? path "http://"))
            (get-external path)
            (r/dispatch app request))))
      (s/with-history debug/*history*)
      (s/with-cache)))

;; --- Public API ---

(defn GET
  "Execute a GET request. Backward-compatible API.
   Supports both in-process and external URLs."
  [request-url]
  (s/GET server request-url))
