(ns rollui.resource)

(declare resource-fetch!)

(defn resource
  "Create a resource atom and immediately start fetching.
   `fetch-fn` is a zero-arg function returning a js/Promise.
   Returns a plain atom with shape {:status :loading/:ready/:error :data _ :error _}."
  [fetch-fn]
  (let [gen (atom 0)
        r   (atom {:status :loading :data nil :error nil})]
    (set! (.-__resource_gen r) gen)
    (set! (.-__resource_last_fn r) fetch-fn)
    (resource-fetch! r fetch-fn)
    r))

(defn resource-fetch!
  "Re-fetch a resource with a new fetch function.
   Sets status to :loading (preserving previous :data),
   calls `fetch-fn`, and updates the atom on resolve/reject.
   Stale results are discarded via generation counter."
  [r fetch-fn]
  (let [gen    (.-__resource_gen ^js r)
        my-gen (swap! gen inc)]
    (set! (.-__resource_last_fn ^js r) fetch-fn)
    (swap! r assoc :status :loading :error nil)
    (-> (fetch-fn)
        (.then  (fn [data]
                  (when (= my-gen @gen)
                    (reset! r {:status :ready :data data :error nil}))))
        (.catch (fn [err]
                  (when (= my-gen @gen)
                    (swap! r assoc :status :error :error err)))))
    r))

(defn resource-refetch!
  "Re-run the most recent fetch function."
  [r]
  (resource-fetch! r (.-__resource_last_fn ^js r)))

(defn lazy-resource
  "Like `resource` but does NOT start fetching immediately.
   Returns an atom in :idle state. Call `resource-refetch!` to trigger the first fetch."
  [fetch-fn]
  (let [gen (atom 0)
        r   (atom {:status :idle :data nil :error nil})]
    (set! (.-__resource_gen r) gen)
    (set! (.-__resource_last_fn r) fetch-fn)
    r))

(defn idle? [v] (= :idle (:status v)))

;; Convenience accessors (work on dereffed values)
(defn loading? [v] (= :loading (:status v)))
(defn ready?   [v] (= :ready   (:status v)))
(defn error?   [v] (= :error   (:status v)))
(defn rdata    [v] (:data v))
(defn rerror   [v] (:error v))
