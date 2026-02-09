(ns api.debug
  "Network tab for the REPL — tools to inspect request/response history
   recorded by api.server/with-history."
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]))

(def ^:dynamic *history* (atom []))

(defn clear-history!
  "Clear the request/response history."
  []
  (reset! *history* []))

(defn history
  "Return the full request/response history.
   Options:
   - :last n — return only the last n entries
   - :method :GET — filter by HTTP method
   - :status :ok/:error — filter by status
   - :path \"str\" — filter by path substring"
  ([] @*history*)
  ([& {:keys [last method status path]}]
   (cond->> @*history*
     method (filter #(= method (get-in % [:request :method])))
     status (filter #(= status (:status %)))
     path   (filter #(clojure.string/includes? (get-in % [:request :path]) path))
     last   (take-last last))))

(defn last-request
  "Return the most recent request map."
  []
  (:request (peek @*history*)))

(defn last-response
  "Return the most recent response."
  []
  (or (:response (peek @*history*))
      (:error (peek @*history*))))

(defn last-entry
  "Return the most recent history entry (request + response + metadata)."
  []
  (peek @*history*))

(defn find-requests
  "Find history entries matching a predicate."
  [pred]
  (filter pred @*history*))

(defn errors
  "Return only error entries from history."
  []
  (filter #(= :error (:status %)) @*history*))

;; ---------------------------------------------------------------------------
;; Pretty-print helper
;; ---------------------------------------------------------------------------

(defn- pprint-value
  "Pretty-print value v. Options: {:full? true} to avoid truncation. Returns the value." 
  ([v] (pprint-value v {}))
  ([v {:keys [full?]}]
   (try
     (if (or (map? v) (vector? v) (seq? v) (set? v))
       (binding [*print-level* (if full? nil 3)
                 *print-length* (if full? nil 200)]
         (pprint v))
       (prn v))
     (catch :default e
       (println "Failed to pprint value:" e)
       (prn v)))
   v))

;; ---------------------------------------------------------------------------
;; Inspect / Printing APIs
;; ---------------------------------------------------------------------------

(defn inspect
  "Pretty-print a history entry or the last entry.
   Usage:
     (inspect)            ;; pretty-print last entry (summarized)
     (inspect entry)      ;; pretty-print specific entry (summarized)
     (inspect entry {:full? true}) ;; deep print"
  ([] (inspect (last-entry) {}))
  ([entry] (inspect entry {}))
  ([entry {:keys [full?]}]
   (let [entry (or entry (last-entry))]
     (when entry
       (println "───────────────────────────────────")
       (println "Request:" (:method (:request entry)) (:path (:request entry)))
       (when (seq (:query (:request entry)))
         (println "  Query:" (:query (:request entry))))
       (when (some? (:body (:request entry)))
         (println "  Body:")
         (pprint-value (:body (:request entry)) {:full? full?}))
       (println "Status:" (:status entry))
       (println "Duration:" (:duration-ms entry) "ms")
       (println "Time:" (:timestamp entry))
       (if (= :ok (:status entry))
         (let [resp (:response entry)]
           (println "Response links:" (keys (:_links resp)))
           (println "Response (pretty):")
           (pprint-value resp {:full? full?})
           (when (:_embedded resp)
             (println "Embedded summary:")
             (doseq [[k v] (:_embedded resp)]
               (println " " (name k) ":" (if (coll? v) (count v) 1)))))
         (println "Error:" (:error entry)))
       (println "───────────────────────────────────")
       entry))))

(defn inspect-by-id
  "Inspect a specific history entry by its id. Optional {:full? true}."
  ([id] (inspect-by-id id {}))
  ([id opts]
   (inspect (first (filter #(= id (:id %)) @*history*)) opts)))

(defn status
  "Print a summary of the request history. If full? true, pretty-print last response."
  ([] (status false))
  ([full?]
   (let [h @*history*
         total (count h)
         ok-count (count (filter #(= :ok (:status %)) h))
         err-count (count (filter #(= :error (:status %)) h))
         durations (map :duration-ms h)
         avg-ms (when (seq durations) (/ (reduce + durations) (count durations)))]
     (println "=== Network Debug Status ===")
     (println "Total requests:" total)
     (println "  Success:" ok-count)
     (println "  Errors:" err-count)
     (when avg-ms
       (println "  Avg duration:" (int avg-ms) "ms")
       (println "  Max duration:" (apply max durations) "ms"))
     (when (seq h)
       (println "\nMost recent request summary:")
       (inspect (peek h) {:full? full?}))
     (println "============================")
     {:total total :ok ok-count :errors err-count :avg-ms avg-ms}))
)
