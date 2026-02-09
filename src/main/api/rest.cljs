(ns api.rest
  "Composable REST routing combinators.
   A handler is (fn [request] -> Promise<HAL-resource> | nil)
   where request is {:path \"/remaining/path\" :method :GET :params {} :query {}
                     :body nil :headers {}}."
  (:require [clojure.string :as str]))

(defn- segment-match
  "Match a single path segment against a pattern segment.
   Returns [match? params]."
  [pattern-seg path-seg]
  (cond
    (= pattern-seg path-seg)
    [true {}]

    (and (string? pattern-seg)
         (str/starts-with? pattern-seg "{")
         (str/ends-with? pattern-seg "}"))
    (let [param-name (keyword (subs pattern-seg 1 (dec (count pattern-seg))))]
      [true {param-name path-seg}])

    :else
    [false {}]))

(defn match-path
  "Match a path pattern like \"/stories/{id}\" against a path string.
   Returns {:params {:id \"123\"}} or nil."
  [pattern path]
  (let [pattern-segs (str/split pattern #"/")
        path-segs (str/split path #"/")]
    (when (= (count pattern-segs) (count path-segs))
      (loop [psegs pattern-segs
             pathsegs path-segs
             params {}]
        (if (empty? psegs)
          {:params params}
          (let [[matched? seg-params] (segment-match (first psegs) (first pathsegs))]
            (when matched?
              (recur (rest psegs) (rest pathsegs) (merge params seg-params)))))))))

(defn route
  "Match an exact path pattern and call handler.
   Returns nil (no match) or a Promise<HAL-resource>."
  [pattern handler-fn]
  (fn [{:keys [path query] :as request}]
    (when-let [{:keys [params]} (match-path pattern path)]
      (handler-fn (merge request {:params (merge (:params request) params)
                                  :query query})))))

(defn routes
  "Try each route in order, return first non-nil result."
  [& route-fns]
  (fn [request]
    (loop [rs route-fns]
      (when (seq rs)
        (or ((first rs) request)
            (recur (rest rs)))))))

(defn mount
  "Strip a path prefix and delegate to a sub-handler."
  [prefix sub-handler]
  (fn [{:keys [path] :as request}]
    (when (str/starts-with? path prefix)
      (let [remaining (subs path (count prefix))
            remaining (if (str/starts-with? remaining "/") remaining (str "/" remaining))]
        (sub-handler (assoc request :path remaining))))))

(defn wrap
  "Wrap a handler with a middleware function.
   middleware-fn receives (handler, request) and must return Promise or nil."
  [handler middleware-fn]
  (fn [request]
    (middleware-fn handler request)))

(defn method-route
  "Match a specific HTTP method AND path pattern."
  [method pattern handler-fn]
  (fn [{:keys [path] :as request}]
    (when (= method (:method request))
      (when-let [{:keys [params]} (match-path pattern path)]
        (handler-fn (merge request {:params (merge (:params request) params)}))))))

(defn dispatch
  "Dispatch a request to a handler. Returns Promise.
   Converts nil (no match) into a rejected promise."
  ([handler request-map]
   (let [request (merge {:method :GET :params {} :query {} :body nil :headers {}} request-map)
         result (handler request)]
     (if result
       result
       (js/Promise.reject (js/Error. (str "No route found for: " (:path request)))))))
  ([handler path query-params]
   (dispatch handler {:path path :query (or query-params {})})))
