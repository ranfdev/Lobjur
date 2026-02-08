(ns lobjur.widgets.user
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.GdkPixbuf" :as Pixbuf]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [lobjur.state :as state]
   [lobjur.utils.http :as http]
   [lobster.core :as lobster]))

(defn pixbuf-to-texture [px]
  (Gdk/Texture.new_for_pixbuf px))
(defn fetch-pixbuf [url]
  (-> (http/get-raw url)
      (.then #(Gio/MemoryInputStream.new_from_bytes %))
      (.then #(Pixbuf/Pixbuf.new_from_stream % nil))))

(defn loaded-user-view [{:keys [username avatar_url karma] :as user}]
  (let [grid (Gtk/Grid. #js {:row_spacing 8
                             :column_spacing 8
                             :halign Gtk/Align.START})]
    (doseq [[i [k v]] (zipmap (range) user)
            :let [key-label
                  (Gtk/Label. #js {:label (name k)
                                   :yalign 0.0
                                   :xalign 0.0})
                  _ (.add_css_class key-label "heading")
                  value-label
                  (Gtk/Label. #js {:label (str v)
                                   :xalign 0.0
                                   :selectable true
                                   :wrap-mode Pango/WrapMode.WORD_CHAR
                                   :wrap true})]]
      (.attach grid key-label 0 i 1 1)
      (.attach grid value-label 1 i 1 1))
    [Gtk/Box
     :orientation Gtk/Orientation.VERTICAL
     :spacing 8
     :.append
     [Gtk/Box
      :spacing 16
      :margin-top 16
      :margin-bottom 16
      :.append
      [Adw/Avatar
       :size 72
       :custom-image
       (.then
        (fetch-pixbuf (lobster/rel avatar_url))
        pixbuf-to-texture)]
      :.append
      [Gtk/Box
       :orientation Gtk/Orientation.VERTICAL
       :valign Gtk/Align.CENTER
       :.append
       [Gtk/Label
        :label username
        :xalign 0
        :.add_css_class "title-1"]
       :.append
       [Gtk/Label
        :xalign 0
        :label (str "Karma: " karma)]]]
     :.append grid
     :.append
     [Gtk/Button
      :label "Newest Stories"
      :.add_css_class "suggested-action"
      :$clicked #(state/send [:push-user-stories username])]]))

(defn user-view [username]
  [Adw/Clamp
   :.add_css_class "background"
   :margin-start 8
   :margin-end 8
   :margin-top 8
   :margin-bottom 8
   :child
   [Gtk/ScrolledWindow
    :propagate-natural-height true
    :child
    (-> (lobster/user username)
        (.then loaded-user-view))]])

