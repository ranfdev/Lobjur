(ns lobjur.widgets.comments
  (:require
   [shadow.cljs.modern :refer-macros [defclass]]
   [lobjur.state :refer [curr-view]]
   [rollui.core :as rollui]
   [lobjur.utils.http :as http]
   [lobjur.utils.common :refer [parse-json]]
   [lobster.core :as lobster]
   [lobjur.widgets.shared :refer [upvote-btn back-to-home-btn]]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.Pango" :as Pango]
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]))

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
      :css_classes #js ["small" "button" "flat" "heading"]]]

    [Gtk/Label
     ::rollui/ref-in [refs :label]
     :selectable true
     :wrap true
     :wrap-mode Pango/WrapMode.WORD_CHAR
     :margin-start 8
     :margin-end 8
     :margin-bottom 8
     :xalign 0.0]
    [Gtk/Box
     :spacing 8
     :margin-start 8
     :orientation Gtk/Orientation.VERTICAL])])

(defn list-setup [_ ^js item]
  (.set_child item (rollui/RefsWidget comment-widget)))
(defn list-bind [_ ^js item]
  (let [data (.-data (.get_item item))
        {:keys [comment_plain indent_level]
         {:keys [username]} :commenting_user} data
        child (.get_child item)
        refs @(.-refs child)]
    (.set_margin_start (:box refs) (* 4 indent_level))
    (.connect (:user-btn refs) "clicked"
              #(reset! curr-view {:name :user :username username
                                  :header-start back-to-home-btn
                                  :prev-state @curr-view}))
    (.set_label (:user-btn refs) username)
    (.set_label (:label refs) comment_plain)))
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
     (let [host (.get_host (.parse_relative lobster/base-url url GLib/UriFlags.NONE))]
       [Gtk/LinkButton
        :css_classes #js ["small" "button" "flat" "caption"]
        :halign Gtk/Align.START
        :uri (str "https://lobste.rs/domain/" host) #_("Meh, not sure about this")
        :label host])
     :.append
     (for [t tags]
       [Gtk/LinkButton
        :uri (str "https://lobste.rs/t/" t)
        :label t
        :valign Gtk/Align.CENTER
        :css_classes #js ["small" "button" "flat" "tag" "caption"]])]
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

