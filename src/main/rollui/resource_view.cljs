(ns rollui.resource-view
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]
   [rollui.core :refer [derived-atom]]
   [rollui.resource :as r]))

(defn resource-widget
  "Derive a widget from a resource atom.
   `opts` is a map:
     :on-ready   (fn [data] -> widget-vector)         REQUIRED
     :on-loading (fn [prev-data] -> widget-vector)     optional, defaults to Adw/Spinner
     :on-error   (fn [error prev-data] -> widget-vector) optional, defaults to StatusPage
   Returns a derived-atom suitable for use as a rollui property value."
  [resource-atom key opts]
  (let [{:keys [on-ready on-loading on-error]
         :or {on-loading (fn [_] [Adw/Spinner
                                  :halign Gtk/Align.CENTER
                                  :valign Gtk/Align.CENTER])
              on-error   (fn [err _] [Adw/StatusPage
                                       :icon_name "dialog-error-symbolic"
                                       :title "Error"
                                       :description (str err)])}}
        opts]
    (derived-atom
     [resource-atom]
     key
     (fn [{:keys [status data error]}]
       (case status
         :idle    (on-loading data)
         :loading (on-loading data)
         :ready   (on-ready data)
         :error   (on-error error data))))))
