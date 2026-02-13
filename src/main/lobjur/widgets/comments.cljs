(ns lobjur.widgets.comments
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gio" :as Gio]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [clojure.string :as str]
   [lobjur.state :as state]
   [lobjur.widgets.shared :refer [html-link-activate pagination-controls time-ago-label upvote-btn]]
   [api.router :as api]
   [api.helpers :refer [external-url fetch-collection hal-link has-relation? next-page prev-page]]
   [lobjur.utils.common :refer [html->text]]
   [rollui.core :refer [derived-atom]]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]
   [html2gtk.core :as h2g]))

(defn- count-descendants [comment]
  (let [replies (or (get-in comment [:_embedded :replies]) [])]
    (+ (count replies) (reduce + 0 (map count-descendants replies)))))

(defn- comment-content-widget [comment depth {:keys [icon-name tooltip-text toggle! chip-widget has-linked-replies?]}]
  (let [{:keys [text author]} comment
        author-href (hal-link comment :author)
        action-group (doto (Gio/SimpleActionGroup.)
                       (.add_action
                        (doto (Gio/SimpleAction. #js {:name "copy-text"})
                          (.connect "activate"
                            (fn [_ _]
                              (let [clipboard (.get_clipboard (Gdk/Display.get_default))]
                                (.set ^js clipboard (or (html->text text) "")))))))
                       (.add_action
                        (doto (Gio/SimpleAction. #js {:name "view-author"})
                          (.connect "activate"
                            (fn [_ _]
                              (state/send [:push-user {:href author-href :title author}]))))))
        menu (doto (Gio/Menu.)
               (.append "Copy Text" "comment.copy-text")
               (.append "View Author" "comment.view-author"))]
    [Gtk/Box
     :.add_css_class "comment"
     :.add_css_class (str "comment-depth-" (mod depth 6))
     :orientation Gtk/Orientation.VERTICAL
     :.append
     (list
       [Gtk/Box
        :.append
         [Gtk/Button
          :icon-name icon-name
          :margin-end 2
          :margin-start 2
          :visible has-linked-replies?
          :tooltip-text tooltip-text
          :css_classes #js ["flat" "circular" "comment-collapse-btn"]
          :$clicked (fn [_] (toggle!))]
        :.append
        [Gtk/Button
        :label author
        :halign Gtk/Align.START
        :css_classes #js ["small" "button" "flat" "heading"]
        :$clicked #(state/send [:push-user {:href author-href :title author}])]
         :.append
         chip-widget
         :.append
         [Gtk/MenuButton
         :$map (fn [widget]
                 (.insert_action_group ^js widget "comment" action-group))
         :icon-name "view-more-symbolic"
         :tooltip-text "Comment options"
         :css_classes #js ["flat" "small" "comment-revealer-btn"]
        :valign Gtk/Align.CENTER
        :menu-model menu]]

        [Adw/Bin
         :hexpand true
         :margin-start 8
         :margin-end 8
         :margin-bottom 8
           :child (h2g/render-html-widget text {:on-link-activate html-link-activate})])]))

(declare comment-tree-widget)

(defn- comment-replies-widget [comment depth replies-visible]
  (if-let [embedded-replies (get-in comment [:_embedded :replies])]
    [Gtk/Box
     :visible replies-visible
     :orientation Gtk/Orientation.VERTICAL
     :margin-start 12
     :spacing 4
     :.append
     (map #(comment-tree-widget % (inc depth)) embedded-replies)]
    (when (get-in comment [:_links :replies :href])
      (let [replies-res (r/resource #(fetch-collection comment :replies {:default []}))]
        [Adw/Bin
         :visible replies-visible
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
               [Gtk/Box]))})]))))

(defn comment-tree-widget
  ([comment] (comment-tree-widget comment 0))
  ([comment depth]
    (let [collapsed (atom false)
          desc-count (count-descendants comment)
          has-linked-replies? (some? (get-in comment [:_links :replies :href]))
          reply-info (cond
                       (pos? desc-count) (str desc-count " " (if (= 1 desc-count) "reply" "replies"))
                       has-linked-replies? "replies"
                       :else nil)
          chip-widget (time-ago-label (:created_at comment)
                                      :suffix (when reply-info (str " · " reply-info))
                                      :hexpand true
                                      :halign Gtk/Align.FILL
                                      :xalign 0.0
                                      :wrap true
                                      :css_classes #js ["dim-label"])
          comment-id (:id comment)
          icon-name (derived-atom [collapsed]
                      (keyword (str "comment-collapse-icon-" comment-id))
                      (fn [is-collapsed]
                        (if is-collapsed "pan-end-symbolic" "pan-down-symbolic")))
          tooltip-text (derived-atom [collapsed]
                         (keyword (str "comment-collapse-tooltip-" comment-id))
                         (fn [is-collapsed]
                           (if is-collapsed "Expand comment" "Collapse comment")))
          replies-visible (derived-atom [collapsed]
                           (keyword (str "comment-replies-visible-" comment-id))
                           not)
          toggle! #(swap! collapsed not)]
     [Gtk/Box
      :orientation Gtk/Orientation.VERTICAL
      :spacing 4
      :.append
      (comment-content-widget comment depth {:icon-name icon-name
                                              :tooltip-text tooltip-text
                                              :toggle! toggle!
                                              :chip-widget chip-widget
                                              :has-linked-replies? has-linked-replies?})
      :.append
      (comment-replies-widget comment depth replies-visible)])))
(defn comments-view [{:keys [title url score tags] :as story}]
  (let [comments-href (get-in story [:_links :comments :href])
        domain-href (hal-link story :domain-stories)
        tag-story-links (get-in story [:_links :tag-stories])
        self-href (hal-link story :self)
        ;; Fetch story text for self-posts (no external URL)
        story-text-res (when (and (not (not-empty url)) self-href)
                         (r/resource #(-> (api/GET self-href)
                                           (.then (fn [full-story]
                                                   (some-> (:text full-story) not-empty))))))
        res (r/resource #(api/GET comments-href))]
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
                  [Adw/Bin
                   :hexpand true
                   :margin-start 8
                   :margin-end 8
                   :child (h2g/render-html-widget text {:on-link-activate html-link-activate})]
                  [Gtk/Box]))
             :on-loading (fn [_] [Gtk/Box])})])
       :.append
       [Adw/Bin
        :child
        (rv/resource-widget
         res :comments
         {:on-ready
          (fn [response]
            (let [comments (get-in response [:_embedded :comments] [])]
              (if (seq comments)
                [Gtk/Box
                 :orientation Gtk/Orientation.VERTICAL
                 :spacing 8
                 :.append
                 (for [c comments]
                   (comment-tree-widget c))
                 :.append
                 (pagination-controls
                  {:prev-sensitive (derived-atom [res] :prev-comments-page
                                    #(and (r/ready? %) (has-relation? (r/rdata %) :prev)))
                   :next-sensitive (derived-atom [res] :next-comments-page
                                    #(and (r/ready? %) (has-relation? (r/rdata %) :next)))
                   :on-prev (fn []
                              (when-let [data (r/rdata @res)]
                                (r/resource-fetch! res (fn [] (prev-page data)))))
                   :on-next (fn []
                               (when-let [data (r/rdata @res)]
                                 (r/resource-fetch! res (fn [] (next-page data)))))})]
                [Adw/StatusPage
                 :title "No comments available"
                 :icon-name "user-invisible-symbolic"
                 :.add_css_class "compact"])))})]]]]))
