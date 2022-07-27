(ns lobjur.utils.http
  (:refer-clojure :exclude [get update])
  (:require
   ["gjs.gi.Soup" :as Soup]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.byteArray" :as ByteArray]))

(js* "~{}._promisify(~{}.Session.prototype, 'send_and_read_async', 'send_and_read_finish')", Gio, Soup)

(def session (new Soup/Session #js {:user-agent "lobjur"}))
(defn get [url]
  (-> (.send_and_read_async
       session
       (Soup/Message.new "GET", url)
       0
       nil)
      (.then (comp
              ByteArray/toString
              ByteArray/fromGBytes))
      (.catch println)))
