(ns lobster.core
  (:require
   [lobjur.utils.http :as http]
   [lobjur.utils.common :refer [parse-json]]
   ["gjs.gi.GLib" :as GLib]))

(def base-url (GLib/Uri.parse "https://lobste.rs/" GLib/UriFlags.NONE))

(defn- rel [& urls]
  (.to_string (reduce
               (fn [base url]
                 (.parse_relative base url GLib/UriFlags.NONE))
               base-url urls)))

(defn hottest [& {:as params}]
  (.then
   (http/get (rel "hottest.json") {:params params})
   parse-json))

(defn active [& {:as params}]
  (.then
   (http/get (rel "active.json") {:params params})
   parse-json))

(defn story [id & {:as params}]
  (.then
   (http/get (rel "s/" (str id ".json")) {:params params})
   parse-json))

(defn tagged [tag & {:as params}]
  (.then
   (http/get (rel "t/" (str tag ".json")) {:params params})
   parse-json))

(defn user [username & {:as params}]
  (.then
   (http/get (rel "u/" (str username ".json")) {:params params})
   parse-json))
