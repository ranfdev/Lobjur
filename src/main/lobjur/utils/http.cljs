(ns lobjur.utils.http
  (:refer-clojure :exclude [get update])
  (:require
   ["gjs.gi.Soup" :as Soup]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.byteArray" :as ByteArray]))

(js* "~{}._promisify(~{}.Session.prototype, 'send_and_read_async', 'send_and_read_finish')", Gio, Soup)

(def session (new Soup/Session #js {:user-agent "lobjur"}))
(defn get-raw [url & {:as options}]
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
      (.then (comp
              ByteArray/toString
              ByteArray/fromGBytes))))

