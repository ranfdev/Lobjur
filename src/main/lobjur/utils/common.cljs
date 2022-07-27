(ns lobjur.utils.common
  (:require
   ["gjs.gi.GLib" :as GLib]))

(defn parse-json [t] (-> t (js/JSON.parse) (js->clj :keywordize-keys true)))
(def base-url-lobster (GLib/Uri.parse "https://lobste.rs/" GLib/UriFlags.NONE))

