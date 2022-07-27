(ns lobjur.widgets.window
  (:require
   [lobjur.widgets.comments :refer [comments-view]]
   [lobjur.widgets.stories-list-view :refer [top-bar stories-list-view compute-stories-url tagged-stories-url]]
   [lobjur.widgets.user :refer [user-view]]
   [rollui.core :as rollui :refer [derived-atom] :refer-macros [defc]]
   [lobjur.state :refer [curr-view]]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Adw" :as Adw]))

(defn view-widget []
  (derived-atom
   [curr-view]
   ::view-widget
   (fn [v]
     (println v)
     (case (:name v)
       :stories (stories-list-view top-bar (compute-stories-url (:stories-kw v) (:page v)))
       :comments (comments-view (:story v))
       :user (user-view (:username v))
       :tag (stories-list-view
             (fn []
               [Gtk/Label
                :margin-top 8
                :margin-bottom 8
                :label (str "Tagged with: " (:tag v))
                :xalign 0.0
                :.add_css_class "title-2"])
             (tagged-stories-url (:tag v)
                                 (:page v)))
       (stories-list-view top-bar (compute-stories-url :hottest 0))))))

(declare header-bar)
(defc header-bar []
  (let [header-start (derived-atom [curr-view] ::header-start #(get % :header-start nil))]
    [Adw/HeaderBar
     :.pack_start [Adw/Bin :child header-start]
     :title_widget [Gtk/Label :label "Lobjur" :css_classes #js ["title-2"]]]))

(defn window-content []
  [Gtk/Box
   :orientation Gtk/Orientation.VERTICAL
   :.append
   (header-bar)
   :.append
   [Adw/Bin :child (view-widget)]])

