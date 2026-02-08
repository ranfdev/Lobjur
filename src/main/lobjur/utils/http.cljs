(ns lobjur.utils.http
  (:refer-clojure :exclude [get update])
  (:require
   ["gi://Soup$default" :as Soup]
   ["gi://Gio$default" :as Gio]))

(js* "~{}._promisify(~{}.Session.prototype, 'send_and_read_async', 'send_and_read_finish')", Gio, Soup)

(def session (new Soup/Session #js {:user-agent "lobjur"}))

(def ^:dynamic *debug-requests* true)

(defn get-raw [url & {:as options}]
  (when *debug-requests*
    (println "HTTP Request:" url (if (:params options) (str "with params: " (:params options)) "")))
  (-> (.send_and_read_async
       session
       (if (:params options)
         (Soup/Message.new_from_encoded_form
          "GET",
          url,
          (Soup/form_encode_hash (clj->js
                                  (into {}
                                        (map
                                         (fn [[k v]] [(name k) (str v)])
                                         (:params options))))))
         (Soup/Message.new "GET", url))
       0
       nil)))

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
