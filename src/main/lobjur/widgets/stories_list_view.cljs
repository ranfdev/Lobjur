(ns lobjur.widgets.stories-list-view
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]
   [lobjur.state :as state :refer [global-widgets]]
   [lobjur.widgets.shared :refer [upvote-btn time-ago]]
   [api.router :as api]
   [api.helpers :refer [fetch-collection external-url next-page prev-page has-relation? hal-link placeholder?]]
   [rollui.core :as rollui :refer [build-ui derived-atom]]
   [rollui.resource :as r]
   [rollui.resource-view :as rv]
   [lobster.core :refer [base-url]]))

(defn story-item-widget
  [{:keys [title url score created_at comment_count tags submitter] :as story}]
  (let [has-url? (not-empty url)]
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
       :.append
       (list
        (if has-url?
          [Gtk/LinkButton
           :hexpand true
           :uri url
           :child
           [Gtk/Label :label title :wrap true :xalign 0.0]
           :css_classes #js ["small" "button" "heading" "flat"]]
          [Gtk/Label :label title :wrap true :xalign 0.0
           :hexpand true
           :css_classes #js ["heading"]
           :margin-start 8 :margin-top 4])
        [Adw/WrapBox
         :child-spacing 8
         :line-spacing 4
         :.append
         (when has-url?
           (let [host (.get_host (.parse_relative base-url url GLib/UriFlags.NONE))]
             [Gtk/Button
              :.add_css_class (list "small" "button" "flat" "caption")
              :halign Gtk/Align.START
              :$clicked #(state/send [:push-domain-stories host])
              :label host]))
         :.append
         (for [t tags]
           [Gtk/Button
            :$clicked #(state/send [:push-tagged-stories t])
            :label t
            :valign Gtk/Align.CENTER
            :.add_css_class (list "small" "flat" "tag" "caption")])]
        [Gtk/Box
         :spacing 2
         :halign Gtk/Align.START
         :.append
         (list
          [Gtk/Button
           :$clicked #(state/send [:push-user submitter])
           :label submitter
           :css_classes #js ["small" "button" "flat" "body"]]
          [Gtk/Label
           :label (time-ago created_at)])])]
      [Gtk/Button
       :valign Gtk/Align.CENTER
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
  (if (placeholder? story)
    (let [res (r/resource #(api/GET (hal-link story :self)))]
      [Adw/Bin
       :child
       (rv/resource-widget
        res :lazy-story
        {:on-ready (fn [full-story] (story-item-widget full-story))
         :on-loading (fn [_] (story-item-placeholder-widget))})])
    (story-item-widget story)))

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
                       :.add_css_class "navigation-sidebar"
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
(def sources
  [{:name "Lobsters"
    :feeds [{:title "Hottest" :url "/feeds/lobsters/hot" :id "hot" :icon "power-profile-performance-symbolic"}
            {:title "Active" :url "/feeds/lobsters/newest" :id "active" :icon "audio-speakers-symbolic"}]}
   {:name "Hacker News"
    :feeds [{:title "Top" :url "/feeds/hn/top" :id "top" :icon "starred-symbolic"}
            {:title "New" :url "/feeds/hn/newest" :id "new" :icon "document-new-symbolic"}
            {:title "Best" :url "/feeds/hn/best" :id "best" :icon "emoji-flags-symbolic"}]}])

(defn build-view-stack [source]
  (let [stack (Adw/ViewStack.)]
    (doseq [{:keys [title url id icon]} (:feeds source)]
      (doto (.add_titled stack
                         (build-ui (stories-list-view url))
                         id
                         title)
        (.set_icon_name icon)))
    stack))

(defn home-stories []
  (let [content-bin (Adw/Bin.)
        view-switcher (doto (Adw/ViewSwitcher.)
                        (.set_policy Adw/ViewSwitcherPolicy.WIDE))
        initial-stack (build-view-stack (first sources))
        dropdown (Gtk/DropDown.
                  #js {:model (Gtk/StringList.
                               #js {:strings #js ["Lobsters" "Hacker News"]})})]
    (.set_child content-bin initial-stack)
    (.set_stack view-switcher initial-stack)
    (.connect dropdown "notify::selected"
              (fn [_]
                (let [idx (.get_selected dropdown)
                      source (nth sources idx)
                      new-stack (build-view-stack source)]
                  (.set_child content-bin new-stack)
                  (.set_stack view-switcher new-stack))))
    (swap! state/global-widgets assoc
           :home-view-switcher view-switcher
           :home-dropdown dropdown)
    [Gtk/Box
     ::rollui/ref-in [global-widgets :home]
     :orientation Gtk/Orientation.VERTICAL
     :.append
     content-bin]))
