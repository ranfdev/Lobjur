(ns api.server
  "A server is just a function `(fn [request-map] -> Promise<response>)`.

   Request map shape:
     {:method  :GET
      :path    \"/lobsters/feeds/hot\"
      :query   {}
      :params  {}
      :body    nil
      :headers {}}

   This namespace provides:
   - Core request functions (`request`, `GET`, `POST`, etc.)
   - Middleware combinators that wrap servers (`with-logging`, `with-timing`, etc.)
   - A composition helper (`->server`) to build server pipelines."
  (:require [api.url :as url]))

;; ---------------------------------------------------------------------------
;; Core request functions
;; ---------------------------------------------------------------------------

(defn request
  "Send a request to a server. Returns a Promise."
  [server request-map]
  (server (merge {:method :GET :params {} :query {} :body nil :headers {}}
                 request-map)))

;; ---------------------------------------------------------------------------
;; Convenience HTTP method functions
;; ---------------------------------------------------------------------------

(defn GET
  "Parse an in-process URL and send a GET request to the server."
  [server url]
  (let [{:keys [path query-params]} (url/parse-url url)]
    (request server {:method :GET :path path :query query-params})))

(defn POST
  "Parse an in-process URL and send a POST request with body to the server."
  [server url body]
  (let [{:keys [path query-params]} (url/parse-url url)]
    (request server {:method :POST :path path :query query-params :body body})))

(defn PUT
  "Parse an in-process URL and send a PUT request with body to the server."
  [server url body]
  (let [{:keys [path query-params]} (url/parse-url url)]
    (request server {:method :PUT :path path :query query-params :body body})))

(defn PATCH
  "Parse an in-process URL and send a PATCH request with body to the server."
  [server url body]
  (let [{:keys [path query-params]} (url/parse-url url)]
    (request server {:method :PATCH :path path :query query-params :body body})))

(defn DELETE
  "Parse an in-process URL and send a DELETE request to the server."
  [server url]
  (let [{:keys [path query-params]} (url/parse-url url)]
    (request server {:method :DELETE :path path :query query-params})))

;; ---------------------------------------------------------------------------
;; Middleware combinators
;; Each takes a server (and optional config) and returns a new server.
;; ---------------------------------------------------------------------------

(defn with-logging
  "Log requests and responses to console."
  [server]
  (fn [request]
    (println ">>>" (:method request) (:path request))
    (let [start (js/Date.now)]
      (-> (server request)
          (.then (fn [response]
                   (println "<<<" (:method request) (:path request)
                            (str (- (js/Date.now) start) "ms"))
                   response))
          (.catch (fn [err]
                    (println "!!!" (:method request) (:path request) (.-message err))
                    (js/Promise.reject err)))))))

(defn with-timing
  "Add :_timing metadata to responses with request duration."
  [server]
  (fn [request]
    (let [start (js/Date.now)]
      (-> (server request)
          (.then (fn [response]
                   (assoc response :_timing {:duration-ms (- (js/Date.now) start)
                                             :timestamp (.toISOString (js/Date.))})))))))

(defn with-history
  "Record all request/response pairs to the given history atom for REPL debugging."
  [server history-atom]
  (fn [request]
    (let [start (js/Date.now)
          entry-id (count @history-atom)]
      (-> (server request)
          (.then (fn [response]
                   (swap! history-atom conj
                          {:id entry-id
                           :request request
                           :response response
                           :duration-ms (- (js/Date.now) start)
                           :timestamp (.toISOString (js/Date.))
                           :status :ok})
                   response))
          (.catch (fn [err]
                    (swap! history-atom conj
                           {:id entry-id
                            :request request
                            :error (.-message err)
                            :duration-ms (- (js/Date.now) start)
                            :timestamp (.toISOString (js/Date.))
                            :status :error})
                    (js/Promise.reject err)))))))

(defn with-defaults
  "Merge default values into every request."
  [server defaults]
  (fn [request]
    (server (merge defaults request))))

(defn- ->query-map
  [query]
  (let [query (cond
                (nil? query) {}
                (map? query) query
                :else (js->clj query :keywordize-keys true))]
    (if (map? query) query {})))

(defn- normalize-cache-target
  [request]
  (let [raw-path (or (:path request) "/")
        {:keys [path query-params]}
        (try
          (url/parse-url raw-path)
          (catch :default _
            {:path raw-path :query-params {}}))]
    {:path path
     :query (->> (merge (->query-map query-params)
                        (->query-map (:query request)))
                 (map (fn [[k v]]
                        [(if (keyword? k) (name k) (str k)) v]))
                 (sort-by first)
                 vec)}))

(defn- cache-key
  [request]
  (let [{:keys [path query]} (normalize-cache-target request)]
    [(:method request) path query (:body request)]))

(defn- cache-bypass?
  [request]
  (false? (:cache? request)))

(defn- cacheable-request?
  [request]
  (and (= :GET (:method request))
       (url/in-process? (:path request))
       (not (cache-bypass? request))))

(defn- store-cache!
  [cache-atom key result]
  (let [promise (-> result
                    (.catch (fn [err]
                              (swap! cache-atom dissoc key)
                              (js/Promise.reject err))))]
    (swap! cache-atom assoc key promise)
    promise))

(defn with-cache
  "Cache in-process GET responses. Set :cache? false in request to bypass."
  ([server]
   (with-cache server (atom {})))
  ([server cache-atom]
   (fn [request]
     (if-not (cacheable-request? request)
       (server request)
       (let [key (cache-key request)]
         (or (get @cache-atom key)
             (when-let [result (server request)]
               (store-cache! cache-atom key result))))))))

(defn with-error-handler
  "Catch errors and wrap them as HAL error resources instead of rejecting."
  [server]
  (fn [request]
    (-> (server request)
        (.catch (fn [err]
                  {:_error true
                   :message (.-message err)
                   :_links {:self {:href (url/make-url :in-process (or (:path request) "/"))}}})))))

;; ---------------------------------------------------------------------------
;; Composition helper
;; ---------------------------------------------------------------------------

(defn ->server
  "Create a server by composing a handler with combinators.
   Each combinator is either a function or a vector [combinator-fn & args].
   Example:
     (->server app
       with-timing
       [with-history my-atom]
       with-logging)"
  [handler & combinators]
  (reduce (fn [s comb]
            (if (vector? comb)
              (apply (first comb) s (rest comb))
              (comb s)))
          handler
          combinators))
