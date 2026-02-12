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

(def source-definitions
  [{:name "Lobsters"
    :id "lobsters"
    :extra-links {:tags "/lobsters/feeds/tags"}
    :feeds [{:title "Hottest" :id "hot" :icon "power-profile-performance-symbolic" :rel :hot}
            {:title "Active" :id "newest" :icon "audio-speakers-symbolic" :rel :newest}]}
   {:name "Hacker News"
    :id "hackernews"
    :extra-links {:search "/hackernews/search"}
    :feeds [{:title "Top" :id "top" :icon "starred-symbolic" :rel :top}
            {:title "New" :id "newest" :icon "document-new-symbolic" :rel :newest}
            {:title "Best" :id "best" :icon "emoji-flags-symbolic" :rel :best}]}])

(defn- source->resource
  [{:keys [id feeds extra-links] :as source}]
  (assoc source :_links
         (merge
          {:self (hal/link (str "/" id))}
          (into {}
                (map (fn [f]
                       [(:rel f) (hal/link (str "/" id "/feeds/" (:id f)))])
                     feeds))
          (into {}
                (map (fn [[k v]] [k (hal/link v)])
                     extra-links)))))

(defn- root-handler [_request]
  (js/Promise.resolve
   (hal/resource
    {:self       (hal/link "/")
     :lobsters   (hal/link "/lobsters")
     :hackernews (hal/link "/hackernews")}
    {:title   "Lobjur API"
     :version "1.0"}
    {:sources (mapv source->resource source-definitions)})))

;; --- Composed application handler ---

(def app
  (r/routes
   (r/route "/"      root-handler)
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
      (s/with-cache)
      (s/with-history debug/*history*)))

;; --- Public API ---

(defn GET
  "Execute a GET request. Backward-compatible API.
   Supports both in-process and external URLs."
  [request-url]
  (s/GET server request-url))
