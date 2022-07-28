(ns lobjur.widgets.shared
  (:require
   [lobjur.state :refer [curr-view]]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]))

(defn upvote-btn [score]
  [Gtk/Box
   :margin-start 8
   :margin-end 8
   :orientation Gtk/Orientation.VERTICAL
   :valign Gtk/Align.CENTER
   :width_request 16
   :height_request 16
   :.append [Gtk/Image :icon_name "pan-up-symbolic"]
   :.append [Gtk/Label :label (str score) :css_classes #js ["heading" "numeric"]]])

(def back-to-home-btn
  [Gtk/Button :icon-name "go-previous" :$clicked #(swap! curr-view get :prev-state)])
