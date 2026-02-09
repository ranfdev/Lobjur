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
   [lobjur.utils.common :refer [html->text]]
   [api.router :as api]
   [api.helpers :refer [hal-link]]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]))

(defn pixbuf-to-texture [px]
  (Gdk/Texture.new_for_pixbuf px))
(defn fetch-pixbuf [url]
  (-> (http/get-raw url)
      (.then #(Gio/MemoryInputStream.new_from_bytes %))
      (.then #(Pixbuf/Pixbuf.new_from_stream % nil))))

(defn loaded-user-view [{:keys [username avatar_url karma about created_at invited_by is_admin is_moderator] :as user}]
  (let [grid (Gtk/Grid. #js {:row_spacing 8
                             :column_spacing 8
                             :halign Gtk/Align.START})
        stories-href (hal-link user :stories)
        about-text (when (seq about) (html->text about))
        fields (cond-> []
                 (seq about-text)
                 (conj ["About" about-text])
                 (seq created_at)
                 (conj ["Joined" created_at])
                 (seq invited_by)
                 (conj ["Invited by" invited_by])
                 is_admin
                 (conj ["Role" "Admin"])
                 (and is_moderator (not is_admin))
                 (conj ["Role" "Moderator"]))]
    (doseq [[i [k v]] (zipmap (range) fields)
            :let [key-label
                  (Gtk/Label. #js {:label k
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
       (when avatar_url
         (.then
          (fetch-pixbuf avatar_url)
          pixbuf-to-texture))]
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
      :$clicked #(state/send [:push-user-stories {:href stories-href :title (str username "'s Stories")}])]]))

(defn user-view [user-href]
  (let [res (r/resource #(api/GET user-href))]
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
      (rv/resource-widget
       res :user
       {:on-ready loaded-user-view})]]))

