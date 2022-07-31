(ns lobjur.widgets.comments
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [lobjur.state :as state]
   [lobjur.widgets.shared :refer [time-ago upvote-btn]]
   [lobster.core :as lobster]
   [rollui.core :as rollui]))

(defn comment-widget [refs]
  [Gtk/Box
   ::rollui/ref-in [refs :box]
   :.add_css_class "comment"
   :orientation Gtk/Orientation.VERTICAL
   :.append
   (list
    [Gtk/Box
     :.append
     [Gtk/Button
      ::rollui/ref-in [refs :user-btn]
      :halign Gtk/Align.START
      :css_classes #js ["small" "button" "flat" "heading"]]
     :.append
     [Gtk/Label
      ::rollui/ref-in [refs :time-ago]]]

    [Gtk/Label
     ::rollui/ref-in [refs :label]
     :selectable true
     :wrap true
     :wrap-mode Pango/WrapMode.WORD_CHAR
     :margin-start 8
     :margin-end 8
     :margin-bottom 8
     :xalign 0.0])])

(defn list-setup [_ ^js item]
  (.set_activatable item false)
  (.set_child item (rollui/RefsWidget comment-widget)))
(defn list-bind [_ ^js item]
  (let [data (.-data (.get_item item))
        {:keys [comment_plain indent_level created_at]
         {:keys [username]} :commenting_user} data
        child (.get_child item)
        refs ^js @(.-refs child)]
    (.set_margin_start ^js (:box refs) (* 4 indent_level))
    (doto ^js (:user-btn refs)
      (.set_label username)
      (.connect "clicked"
                #(state/send [:push-user username])))
    (.set_label ^js (:time-ago refs) (time-ago created_at))
    (.set_label ^js (:label refs) comment_plain)))

(defn comments-list-view [comments]
  (let [store (Gio/ListStore. (.-$gtype rollui/DataObject))
        _ (doseq [c comments]
            (.append store (rollui/DataObject. c)))
        selection-model (doto (Gtk/NoSelection. store)
                          ;; doesn't work setting it in the constructor...)
                          (.set_model store))
        factory (doto (Gtk/SignalListItemFactory.new)
                  (.connect "setup" list-setup)
                  (.connect "bind" list-bind))]
    (Gtk/ListView.new selection-model factory)))

(defn comments-view [{:keys [title url score tags short_id]}]
  [Adw/Clamp
   :hexpand true
   :.add_css_class "background"
   :child
   [Gtk/Box
    :orientation Gtk/Orientation.VERTICAL
    :spacing 8
    :margin-top 8
    :margin-start 8
    :margin-end 8
    :.append
    [Gtk/Box
     :.append
     (list
      (upvote-btn score)
      [Gtk/LinkButton
       :hexpand true
       :uri url
       :child
       [Gtk/Label :label title :wrap true :wrap-mode Pango/WrapMode.WORD_CHAR :xalign 0.0]
       :css_classes #js ["button" "title-4" "flat"]])]
    :.append
    [Gtk/Box
     :spacing 8
     :.append
     (let [host
           ; maybe I should get only the base domain...
           (.get_host (.parse_relative lobster/base-url url GLib/UriFlags.NONE))]
       [Gtk/LinkButton
        :css_classes #js ["small" "button" "flat" "caption"]
        :halign Gtk/Align.START
        :uri (lobster/rel "domain/" host)
        :label host])
     :.append
     (for [t tags]
       [Gtk/Button
        :label t
        :valign Gtk/Align.CENTER
        :$clicked #(state/send [:push-tagged-stories t])
        :.add_css_class (list "small" "flat" "tag" "caption")])]
    :.append
    (-> (lobster/story short_id)
        (.then
         (fn [comments]
           (if (> (count (:comments comments)) 0)
             [Gtk/ScrolledWindow
              :propagate-natural-height true
              :vexpand true
              :child
              (comments-list-view (:comments comments))]
             [Adw/StatusPage
              :title "No comments available"
              :icon-name "user-invisible-symbolic"
              :.add_css_class "compact"]))))]])

