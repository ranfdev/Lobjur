(ns lobjur.utils.http
  (:refer-clojure :exclude [get update])
  (:require
   ["gjs.gi.Soup" :as Soup]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.GLib" :as GLib]))

(js* "~{}._promisify(~{}.Session.prototype, 'send_and_read_async', 'send_and_read_finish')", Gio, Soup)

(def session (new Soup/Session #js {:user-agent "lobjur"}))

(def ^:dynamic *debug-requests* true)
(def ^:dynamic *request-timeout-ms* 30000) ; 30 seconds

(defn get-raw [url & {:as options}]
  (when *debug-requests*
    (println "HTTP Request:" url (if (:params options) (str "with params: " (:params options)) "")))
  (let [message (if (:params options)
                  (Soup/Message.new_from_encoded_form
                   "GET",
                   url,
                   (Soup/form_encode_hash (clj->js
                                           (into {}
                                                 (map
                                                  (fn [[k v]] [(name k) (str v)])
                                                  (:params options))))))
                  (Soup/Message.new "GET", url))
        cancellable (Gio/Cancellable.new)
        timeout-id (atom nil)]
    ;; Set up timeout that cancels the request
    (reset! timeout-id
            (GLib/timeout_add
             GLib/PRIORITY_DEFAULT
             *request-timeout-ms*
             (fn []
               (when *debug-requests*
                 (println "HTTP Timeout: Cancelling request to" url))
               (.cancel cancellable)
               false))) ; Return false to not repeat
    (-> (.send_and_read_async
         session
         message
         0
         cancellable)
        (.then (fn [result]
                 ;; Clear timeout on success
                 (when @timeout-id
                   (GLib/source_remove @timeout-id)
                   (reset! timeout-id nil))
                 result))
        (.catch (fn [error]
                  ;; Clear timeout on error
                  (when @timeout-id
                    (GLib/source_remove @timeout-id)
                    (reset! timeout-id nil))
                  (let [error-msg (if (.-message error)
                                    (.-message error)
                                    (str error))]
                    (println "HTTP Error:" url "-" error-msg)
                    (js/Promise.reject (js/Error. (str "HTTP request failed for " url ": " error-msg)))))))))

(defn get [url & {:as options}]
  (-> (get-raw url options)
      (.then (comp #(. (js/TextDecoder.) decode %) #(. ^js % get_data)))
      (.then (fn [data]
               (when *debug-requests*
                 (let [truncated (if (> (count data) 200)
                                   (str (subs data 0 200) "...")
                                   data)]
                   (println "HTTP Response:" url "length:" (count data) "content:" truncated)))
               data))))
