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
   [lobjur.widgets.shared :refer [html-link-activate]]
   [api.router :as api]
   [api.helpers :refer [hal-link]]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]
   [html2gtk.core :as h2g]))

(defn pixbuf-to-texture [px]
  (Gdk/Texture.new_for_pixbuf px))

(defn fetch-pixbuf [url]
  (-> (http/get-raw url)
      (.then #(Gio/MemoryInputStream.new_from_bytes %))
      (.then #(Pixbuf/Pixbuf.new_from_stream % nil))
      (.catch (fn [error]
                 (println "Failed to load avatar image from" url ":" (.-message error))
                 ;; Return nil to signal failure - rollui will skip setting the property
                 nil))))

(defn- user-field-row [label value href]
  [Gtk/Box
   :orientation Gtk/Orientation.HORIZONTAL
   :spacing 8
   :.append
   (list
    [Gtk/Label
     :label label
     :xalign 0.0
     :css_classes #js ["heading"]]
    (if href
      [Gtk/Button
       :label (str value)
       :halign Gtk/Align.START
       :css_classes #js ["button" "flat" "small"]
       :$clicked #(state/send [:push-user {:href href :title value}])]
      [Gtk/Label
       :label (str value)
       :xalign 0.0
       :selectable true
       :wrap-mode Pango/WrapMode.WORD_CHAR
       :wrap true]))])

(defn loaded-user-view [{:keys [username avatar_url karma about created_at invited_by is_admin is_moderator] :as user}]
  (let [stories-href (hal-link user :stories)
        invited-by-href (hal-link user :invited_by)
        about-widget (when (seq about)
                       [Gtk/Box
                        :orientation Gtk/Orientation.HORIZONTAL
                        :spacing 8
                        :.append
                         [Gtk/Label
                          :label "About"
                          :xalign 0.0
                          :yalign 0.0
                          :css_classes #js ["heading"]]
                         :.append
                          (h2g/render-html-widget about {:on-link-activate html-link-activate})])
        fields (cond-> []
                 (seq created_at)
                 (conj ["Joined" created_at nil])
                 (seq invited_by)
                 (conj ["Invited by" invited_by invited-by-href])
                 is_admin
                 (conj ["Role" "Admin" nil])
                 (and is_moderator (not is_admin))
                 (conj ["Role" "Moderator" nil]))
        field-rows (map (fn [[label value href]] (user-field-row label value href)) fields)
        detail-rows (into (cond-> (list)
                            about-widget
                            (conj about-widget))
                          field-rows)]
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
       :text username
       :custom-image
       (when avatar_url
         (.then
          (fetch-pixbuf avatar_url)
          (fn [pixbuf]
            (when pixbuf  ; Only convert if pixbuf loaded successfully
              (pixbuf-to-texture pixbuf)))))]
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
     :.append
     [Gtk/Box
      :orientation Gtk/Orientation.VERTICAL
      :spacing 8
      :.append (if (seq detail-rows) detail-rows [])
      ]
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
