(ns lobjur.widgets.stories-list-view
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Pango" :as Pango]
   ["gjs.gi.Gtk" :as Gtk]
   [clojure.string :as str]
   [lobjur.state :as state :refer [global-widgets]]
   [lobjur.widgets.shared :refer [upvote-btn time-ago]]
   [api.router :as api]
   [api.sources :as sources]
   [api.helpers :refer [fetch-collection external-url next-page prev-page has-relation? hal-link placeholder?]]
   [rollui.core :as rollui :refer [build-ui derived-atom]]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]))

(defn story-item-widget
  [{:keys [title url score created_at comment_count tags submitter] :as story}]
  (let [has-url? (not-empty url)
        domain-href (hal-link story :domain-stories)
        tag-story-links (get-in story [:_links :tag-stories])]
    [Gtk/Box
     :orientation Gtk/Orientation.HORIZONTAL
     :margin-top 4
     :margin-bottom 4
     :margin-start 8
     :margin-end 4
     :.append
     (list
      (upvote-btn score)
      [Gtk/Box
       :orientation Gtk/Orientation.VERTICAL
       :hexpand true
       :.append
       (list
        (if has-url?
          [Gtk/Label
           :margin-top 4
           :margin-bottom 4
           :xalign 0.0
           :label title
           :wrap true
           :wrap-mode Pango/WrapMode.WORD_CHAR
           :css_classes #js ["small" "button" "heading" "flat"]]
          [Gtk/Label :label title :wrap true :xalign 0.0
           :hexpand true
           :css_classes #js ["heading"]
           :margin-start 8 :margin-top 4])
        [Adw/WrapBox
         :child-spacing 8
         :line-spacing 4
         :.append
         (when domain-href
           (let [host (get-in story [:_links :domain-stories :name]
                              ;; fallback: extract host from external URL
                              (some-> (hal-link story :external)
                                      (str/replace #"^https?://" "")
                                      (str/replace #"/.*" "")))]
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
            :$clicked #(state/send [:push-tagged-stories {:href href :title (str t " Stories")}])
            :label t
            :valign Gtk/Align.CENTER
            :.add_css_class (list "small" "flat" "tag" "caption")])]
        [Gtk/Box
         :spacing 2
         :halign Gtk/Align.START
         :.append
         (list
          [Gtk/Button
           :$clicked #(state/send [:push-user {:href (hal-link story :author) :title submitter}])
           :label submitter
           :css_classes #js ["small" "button" "flat" "body"]]
          [Gtk/Label
           :label (time-ago created_at)])])]
      [Gtk/Button
       :valign Gtk/Align.CENTER
       :tooltip-text "View comments"
       :css_classes #js ["button" "flat"]
       :$clicked #(state/send [:select-story story])
       :child
       [Gtk/Overlay
        :child
        [Gtk/Image :pixel_size 28 :opacity 0.5 :icon_name "user-idle-symbolic"]
        :.add_overlay [Gtk/Label
                       :css_classes #js ["caption-heading" "numeric"]
                       :label (str comment_count)]]])]))

(defn- story-item-placeholder-widget []
  [Gtk/Box
   :orientation Gtk/Orientation.HORIZONTAL
   :margin-top 4 :margin-bottom 4 :margin-start 8 :margin-end 4
   :height-request 72
   :.append [Adw/Spinner :halign Gtk/Align.CENTER :valign Gtk/Align.CENTER]])

(defn- lazy-story-widget
  "Render a story, fetching it first if it's a placeholder."
  [story]
  (let [has-url? (not-empty (:url story))]
    (if (placeholder? story)
      (let [res (r/resource #(api/GET (hal-link story :self)))]
        [Gtk/ListBoxRow
         :activatable true
         :story story
         :.add_css_class (list "story-row")
         :can_focus true
         :child
         [Adw/Bin
          :child
          (rv/resource-widget
           res :lazy-story
           {:on-ready (fn [full-story] (story-item-widget full-story))
            :on-loading (fn [_] (story-item-placeholder-widget))})]])
      [Gtk/ListBoxRow
       :activatable true
       :story story
       :.add_css_class (list "story-row")
       :can_focus true
       :child (story-item-widget story)])))

(defn stories-list-view [initial-url]
  (let [res (r/lazy-resource #(api/GET initial-url))]
    [Gtk/ScrolledWindow
     :$map (fn [_] (when (r/idle? @res) (r/resource-refetch! res)))

     :propagate_natural_height true
     :hscrollbar_policy Gtk/PolicyType.NEVER
     :child
     [Adw/Clamp
      :child
      [Gtk/Box
       :orientation Gtk/Orientation.VERTICAL
       :margin-top 8
       :margin-bottom 24
       :margin-start 8
       :margin-end 8
       :spacing 8
       :.append
       [Adw/Bin
        :child
        (rv/resource-widget
         res :stories
         {:on-ready
          (fn [response]
            [Adw/Bin
             :height-request 48
             :child
             (-> (fetch-collection response :stories)
                 (.then
                  (fn [stories]
                    (if (> (count stories) 0)
                      [Gtk/ListBox
                       :activate-on-single-click true
                       :.add_css_class "navigation-sidebar"
                       :$row-activated (fn [_ row]
                                         (when-let [s (aget row "story")]
                                           (let [u (or (get s :url) (aget s "url"))]
                                             (state/send [:select-story (assoc s :initial-view (if (not-empty u) :article :comments))]))))
                       :.append
                       (map lazy-story-widget stories)]
                      [Adw/StatusPage
                       :icon_name "mail-read-symbolic"
                       :title
                       "No Stories Available"]))))])})]
       :.append
       [Gtk/Box
        :hexpand true
        :homogeneous true
        :.append
        [Gtk/Button
         :halign Gtk/Align.START
         :label "Previous"
         :sensitive (derived-atom [res] :prev-story-page
                     #(and (r/ready? %) (has-relation? (r/rdata %) :prev)))
         :$clicked (fn [_] (when-let [data (r/rdata @res)]
                          (r/resource-fetch! res (fn [] (prev-page data)))))]
        :.append
        [Gtk/Label :label "•"]
        :.append
        [Gtk/Button
         :halign Gtk/Align.END
         :label "Next"
         :sensitive (derived-atom [res] :next-story-page
                     #(and (r/ready? %) (has-relation? (r/rdata %) :next)))
         :$clicked (fn [_] (when-let [data (r/rdata @res)]
                          (r/resource-fetch! res (fn [] (next-page data)))))]]]]]))
(defn build-view-stack [source]
  (let [stack (Adw/ViewStack.)
        src-id (:id source)]
    (doseq [{:keys [title id icon]} (:feeds source)]
      (doto (.add_titled stack
                         (build-ui (stories-list-view (str "/feeds/" src-id "/" id)))
                         id
                         title)
        (.set_icon_name icon)))
    stack))

(defn home-stories []
  (let [content-bin (Adw/Bin.)
        initial-stack (build-view-stack (first sources/sources))
        dropdown (Gtk/DropDown.
                  #js {:model (Gtk/StringList.
                               #js {:strings (into-array (mapv :name sources/sources))})})]
    (.set_child content-bin initial-stack)
    (when-let [bar (:sidebar-view-switcher-bar @state/global-widgets)]
      (.set_stack ^js bar initial-stack))
    (.connect dropdown "notify::selected"
              (fn [_]
                (let [idx (.get_selected dropdown)
                      source (nth sources/sources idx)
                      new-stack (build-view-stack source)]
                  (.set_child content-bin new-stack)
                  (when-let [bar (:sidebar-view-switcher-bar @state/global-widgets)]
                    (.set_stack ^js bar new-stack)))))
    (swap! state/global-widgets assoc
           :home-dropdown dropdown)
    [Gtk/Box
     ::rollui/ref-in [global-widgets :home]
     :orientation Gtk/Orientation.VERTICAL
     :.append
     content-bin]))
