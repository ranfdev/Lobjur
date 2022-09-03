(ns lobjur.widgets.stories-list-view
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]
   [lobjur.state :as state :refer [global-widgets]]
   [lobjur.widgets.shared :refer [upvote-btn time-ago]]
   [lobster.core :as lobster]
   [rollui.core :as rollui :refer [build-ui derived-atom]]))

(defn story-item-widget
  [{:keys [title url score created_at comment_count tags] :as story
    {:keys [username]} :submitter_user}]
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
      [Gtk/LinkButton
       :hexpand true
       :uri url
       :child
       [Gtk/Label :label title :wrap true :xalign 0.0]
       :css_classes #js ["small" "button" "heading" "flat"]]
      [Gtk/FlowBox
       :spacing 8
       :selection-mode Gtk/SelectionMode.NONE
       :.append
       (let [host (.get_host (.parse_relative lobster/base-url url GLib/UriFlags.NONE))]
         [Gtk/Button
          :.add_css_class (list "small" "button" "flat" "caption")
          :halign Gtk/Align.START
          :$clicked #(state/send [:push-domain-stories host])
          :label host])
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
         :$clicked #(state/send [:push-user username])
         :label username
         :css_classes #js ["small" "button" "flat" "body"]]
        [Gtk/Label
         :label (time-ago created_at)])])]
    [Gtk/Button
     :valign Gtk/Align.CENTER
     :css_classes #js ["button" "flat"]
     :$clicked #(state/send [:push-story story])
     :child
     [Gtk/Overlay
      :child
      [Gtk/Image :pixel_size 28 :opacity 0.5 :icon_name "user-idle-symbolic"]
      :.add_overlay [Gtk/Label
                     :css_classes #js ["caption-heading" "numeric"]
                     :label (str comment_count)]]])])

(defn stories-list-view [provider]
  (let [page (atom 1)]
    [Gtk/ScrolledWindow
     :.add_css_class "background"
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
        (derived-atom
         [page]
         :stories
         (fn [p]
           [Adw/Bin ;; quickly swaps the widget when the atom changes
            :height-request 48
            :child
            (-> (provider :page p) ;; load the data and display the new listbox
                (.then
                 #(if (> (count %) 0)
                    [Gtk/ListBox
                     :.add_css_class "boxed-list"
                     :.append
                     (map story-item-widget %)]
                    [Adw/StatusPage
                     :icon_name "mail-read-symbolic"
                     :title
                     "No Stories Available"])))]))]
       :.append
       [Gtk/Box
        :hexpand true
        :homogeneous true
        :.append
        [Gtk/Button
         :halign Gtk/Align.START
         :label "Previous"
         :sensitive (derived-atom [page] :prev-story-page #(> % 1))
         :$clicked #(swap! page dec)]
        :.append
        [Gtk/Label
         :label
         (derived-atom [page] :stories-page-label #(str "Page " %))]
        :.append
        [Gtk/Button
         :halign Gtk/Align.END
         :label "Next"
         :$clicked #(swap! page inc)]]]]]))

(defn home-stories []
  (let [stack (Adw/ViewStack.)]
    (doto (.add_titled stack
                       (build-ui (stories-list-view lobster/hottest))
                       "hottest"
                       "Hottest")
      (.set_icon_name "power-profile-performance-symbolic"))
    (doto (.add_titled stack
                       (build-ui (stories-list-view lobster/active))
                       "active" "Active")
      (.set_icon_name "audio-speakers-symbolic"))
    (swap! state/global-widgets assoc :home-stories stack)
    [Gtk/Box
     ::rollui/ref-in [global-widgets :home]
     :orientation Gtk/Orientation.VERTICAL
     :.append
     stack]))

