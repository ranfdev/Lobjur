(ns lobjur.widgets.comments
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [clojure.string :as str]
   [lobjur.state :as state]
   [lobjur.widgets.shared :refer [time-ago upvote-btn]]
   [api.router :as api]
   [api.helpers :refer [external-url fetch-collection hal-link]]
   [rollui.core :as rollui]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]))

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
              ;; Note: replies are embedded by design for nested comments,
              ;; not fetched separately. This is the one legitimate use of
              ;; direct _embedded access for nested structures.
              (when-let [replies (get-in comment [:_embedded :replies])]
                (flatten-comments replies (inc level)))))
           comments)))

(defn list-setup [_ ^js item]
  (.set_activatable item false)
  (.set_child item (rollui/RefsWidget comment-widget)))
(defn list-bind [_ ^js item]
  (let [data (.-data (.get_item item))
        {:keys [text indent_level created_at author] :as comment-data} data
        author-href (hal-link comment-data :author)
        child (.get_child item)
        refs ^js @(.-refs child)]
    (.set_margin_start ^js (:box refs) (* 4 indent_level))
    (doto ^js (:user-btn refs)
      (.set_label author)
      (.connect "clicked"
                #(state/send [:push-user {:href author-href :title author}])))
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

(defn comments-view [{:keys [title url score tags] :as story}]
  (let [comments-href (get-in story [:_links :comments :href])
        domain-href (hal-link story :domain-stories)
        tag-story-links (get-in story [:_links :tag-stories])
        res (r/resource #(-> (api/GET comments-href)
                             (.then (fn [response]
                                      (fetch-collection response :comments)))))]
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
       (when domain-href
         (let [host (some-> (external-url story)
                            (str/replace #"^https?://" "")
                            (str/replace #"/.*" ""))]
           [Gtk/Button
            :.add_css_class (list "small" "button" "flat" "caption")
            :halign Gtk/Align.START
            :$clicked #(state/send [:push-domain-stories {:href domain-href :title (str host " Stories")}])
            :label host]))
       :.append
       (for [tl tag-story-links
             :let [t (:name tl)
                   href (:href tl)]]
         [Gtk/Button
          :label t
          :valign Gtk/Align.CENTER
          :$clicked #(state/send [:push-tagged-stories {:href href :title (str t " Stories")}])
          :.add_css_class (list "small" "flat" "tag" "caption")])]
      :.append
      [Adw/Bin
       :child
       (rv/resource-widget
        res :comments
        {:on-ready
         (fn [comments]
           (let [flat-comments (flatten-comments comments)]
             (if (> (count flat-comments) 0)
               [Gtk/ScrolledWindow
                :propagate-natural-height true
                :vexpand true
                :child
                (comments-list-view flat-comments)]
               [Adw/StatusPage
                :title "No comments available"
                :icon-name "user-invisible-symbolic"
                :.add_css_class "compact"])))})]]]))
