(ns lobjur.widgets.comments
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [clojure.string :as str]
   [lobjur.state :as state]
   [lobjur.widgets.shared :refer [time-ago upvote-btn]]
   [api.router :as api]
   [api.helpers :refer [external-url fetch-collection hal-link]]
   [lobjur.utils.common :refer [html->text]]
   [rollui.core :as rollui]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]))

(defn- comment-content-widget [comment depth]
  (let [{:keys [text created_at author]} comment
        author-href (hal-link comment :author)
        action-group (doto (Gio/SimpleActionGroup.)
                       (.add_action
                        (doto (Gio/SimpleAction. #js {:name "copy-text"})
                          (.connect "activate"
                            (fn [_ _]
                              (let [clipboard (.get_clipboard (Gdk/Display.get_default))]
                                (.set ^js clipboard text))))))
                       (.add_action
                        (doto (Gio/SimpleAction. #js {:name "view-author"})
                          (.connect "activate"
                            (fn [_ _]
                              (state/send [:push-user {:href author-href :title author}]))))))
        menu (doto (Gio/Menu.)
               (.append "Copy Text" "comment.copy-text")
               (.append "View Author" "comment.view-author"))
        box-ref (atom nil)]
    (add-watch box-ref ::install-actions
      (fn [_ _ _ widget]
        (when widget
          (.insert_action_group ^js widget "comment" action-group))))
    [Gtk/Box
     ::rollui/ref box-ref
     :.add_css_class "comment"
     :.add_css_class (str "comment-depth-" (mod depth 6))
     :orientation Gtk/Orientation.VERTICAL
     :.append
     (list
      [Gtk/Box
       :.append
       [Gtk/Button
        :label author
        :halign Gtk/Align.START
        :css_classes #js ["small" "button" "flat" "heading"]
        :$clicked #(state/send [:push-user {:href author-href :title author}])]
       :.append
       [Gtk/Label :label (time-ago created_at) :hexpand true :halign Gtk/Align.START]
       :.append
       [Gtk/MenuButton
        :icon-name "view-more-symbolic"
        :tooltip-text "Comment options"
        :css_classes #js ["flat" "small" "comment-revealer-btn"]
        :valign Gtk/Align.CENTER
        :menu-model menu]]

      [Gtk/Label
       :label text
       :selectable true
       :wrap true
       :wrap-mode Pango/WrapMode.WORD_CHAR
       :margin-start 8
       :margin-end 8
       :margin-bottom 8
       :xalign 0.0])]))

(defn comment-tree-widget
  ([comment] (comment-tree-widget comment 0))
  ([comment depth]
   [Gtk/Box
    :orientation Gtk/Orientation.VERTICAL
    :spacing 4
    :.append
    (comment-content-widget comment depth)
    :.append
    (if-let [embedded-replies (get-in comment [:_embedded :replies])]
      ;; Lobsters: embedded, render directly
      [Gtk/Box
       :orientation Gtk/Orientation.VERTICAL
       :margin-start 12
       :spacing 4
       :.append
       (map #(comment-tree-widget % (inc depth)) embedded-replies)]
      ;; HN: linked, fetch lazily with resource
      (when (get-in comment [:_links :replies :href])
        (let [replies-res (r/resource #(fetch-collection comment :replies {:default []}))]
          [Adw/Bin
           :margin-start 12
           :child
           (rv/resource-widget
            replies-res (keyword (str "replies-" (:id comment)))
            {:on-ready
             (fn [replies]
               (if (seq replies)
                 [Gtk/Box
                  :orientation Gtk/Orientation.VERTICAL
                  :spacing 4
                  :.append
                  (map #(comment-tree-widget % (inc depth)) replies)]
                 [Gtk/Box]))})])))]))


(defn comments-view [{:keys [title url score tags] :as story}]
  (let [comments-href (get-in story [:_links :comments :href])
        domain-href (hal-link story :domain-stories)
        tag-story-links (get-in story [:_links :tag-stories])
        self-href (hal-link story :self)
        ;; Fetch story text for self-posts (no external URL)
        story-text-res (when (and (not (not-empty url)) self-href)
                         (r/resource #(-> (api/GET self-href)
                                          (.then (fn [full-story]
                                                   (some-> (:text full-story)
                                                           not-empty
                                                           html->text))))))
        res (r/resource #(-> (api/GET comments-href)
                             (.then (fn [response]
                                      (fetch-collection response :comments)))))]
    [Gtk/ScrolledWindow
     :hexpand true
     :vexpand true
     :hscrollbar_policy Gtk/PolicyType.NEVER
     :child
     [Adw/Clamp
      :.add_css_class "background"
      :child
      [Gtk/Box
       :orientation Gtk/Orientation.VERTICAL
       :spacing 8
       :margin-top 8
       :margin-start 8
       :margin-end 8
       :margin-bottom 24
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
       (when story-text-res
         [Adw/Bin
          :child
          (rv/resource-widget
           story-text-res :story-text
           {:on-ready
            (fn [text]
              (if text
                [Gtk/Label
                 :label text
                 :wrap true
                 :wrap-mode Pango/WrapMode.WORD_CHAR
                 :selectable true
                 :xalign 0.0
                 :margin-start 8
                 :margin-end 8]
                [Gtk/Box]))
            :on-loading (fn [_] [Gtk/Box])})])
       :.append
       [Adw/Bin
        :child
        (rv/resource-widget
         res :comments
         {:on-ready
          (fn [comments]
            (if (seq comments)
              [Gtk/Box
               :orientation Gtk/Orientation.VERTICAL
               :spacing 8
               :.append
               (for [c comments]
                 (comment-tree-widget c))]
              [Adw/StatusPage
               :title "No comments available"
               :icon-name "user-invisible-symbolic"
               :.add_css_class "compact"]))})]]]]))