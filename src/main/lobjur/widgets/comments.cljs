(ns lobjur.widgets.comments
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [lobjur.state :as state]
   [lobjur.widgets.shared :refer [time-ago upvote-btn]]
   [api.router :as api]
   [api.helpers :refer [external-url hal-collection]]
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

(defn- flatten-comments
  "Flatten a nested comment tree into a list with indent levels."
  ([comments] (flatten-comments comments 0))
  ([comments level]
   (mapcat (fn [comment]
             (cons
              (assoc comment :indent_level level)
              (when-let [replies (get-in comment [:_embedded :replies])]
                (flatten-comments replies (inc level)))))
           comments)))

(defn list-setup [_ ^js item]
  (.set_activatable item false)
  (.set_child item (rollui/RefsWidget comment-widget)))
(defn list-bind [_ ^js item]
  (let [data (.-data (.get_item item))
        {:keys [text indent_level created_at author]} data
        child (.get_child item)
        refs ^js @(.-refs child)]
    (.set_margin_start ^js (:box refs) (* 4 indent_level))
    (doto ^js (:user-btn refs)
      (.set_label author)
      (.connect "clicked"
                #(state/send [:push-user author])))
    (.set_label ^js (:time-ago refs) (time-ago created_at))
    (.set_label ^js (:label refs) text)))

(defn comments-list-view [comments]
  (let [store (Gio/ListStore. (.-$gtype rollui/DataObject))
        _ (doseq [c comments]
            (.append store (rollui/DataObject. c)))
        selection-model (doto (Gtk/NoSelection.)
                          ;; doesn't work setting it in the constructor...)
                          (.set_model store))
        factory (doto (Gtk/SignalListItemFactory.new)
                  (.connect "setup" list-setup)
                  (.connect "bind" list-bind))]
    (Gtk/ListView.new selection-model factory)))

(defn comments-view [{:keys [id title url score tags]}]
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
     (let [host (.get_host (.parse_relative (js/URL. "https://lobste.rs") url GLib/UriFlags.NONE))]
       [Gtk/Button
        :.add_css_class (list "small" "button" "flat" "caption")
        :halign Gtk/Align.START
        :$clicked #(state/send [:push-domain-stories host])
        :label host])
     :.append
     (for [t tags]
       [Gtk/Button
        :label t
        :valign Gtk/Align.CENTER
        :$clicked #(state/send [:push-tagged-stories t])
        :.add_css_class (list "small" "flat" "tag" "caption")])]
    :.append
    (-> (api/GET (str "/stories/" id "/comments"))
        (.then
         (fn [response]
           (let [comments (flatten-comments (hal-collection response :comments))]
             (if (> (count comments) 0)
               [Gtk/ScrolledWindow
                :propagate-natural-height true
                :vexpand true
                :child
                (comments-list-view comments)]
               [Adw/StatusPage
                :title "No comments available"
                :icon-name "user-invisible-symbolic"
                :.add_css_class "compact"])))))]])

