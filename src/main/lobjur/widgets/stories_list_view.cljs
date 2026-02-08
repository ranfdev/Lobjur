(ns lobjur.widgets.stories-list-view
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]
   [lobjur.state :as state :refer [global-widgets]]
   [lobjur.widgets.shared :refer [upvote-btn time-ago]]
   [api.router :as api]
   [api.helpers :refer [fetch-collection external-url next-page prev-page has-relation?]]
   [rollui.core :as rollui :refer [build-ui derived-atom]]
   [lobster.core :refer [base-url]]))

(defn story-item-widget
  [{:keys [title url score created_at comment_count tags submitter] :as story}]
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
       (let [host (.get_host (.parse_relative base-url url GLib/UriFlags.NONE))]
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
         :$clicked #(state/send [:push-user submitter])
         :label submitter
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

(defn stories-list-view [initial-url]
  (let [hal-response (atom nil)]
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
         [hal-response]
         :stories
         (fn [response]
           [Adw/Bin
            :height-request 48
            :child
            (-> (if response
                  (js/Promise.resolve response)
                  (api/GET initial-url))
                (.then (fn [resp]
                         (reset! hal-response resp)
                         ;; Use fetch-collection to properly follow HATEOAS:
                         ;; checks _embedded first, falls back to fetching link if needed
                         (fetch-collection resp :stories)))
                (.then
                 (fn [stories]
                   (if (> (count stories) 0)
                     [Gtk/ListBox
                      :.add_css_class "boxed-list"
                      :.append
                      (map story-item-widget stories)]
                     [Adw/StatusPage
                      :icon_name "mail-read-symbolic"
                      :title
                      "No Stories Available"]))))]))]
       :.append
       [Gtk/Box
        :hexpand true
        :homogeneous true
        :.append
        [Gtk/Button
         :halign Gtk/Align.START
         :label "Previous"
         :sensitive (derived-atom [hal-response] :prev-story-page #(and % (has-relation? % :prev)))
         :$clicked #(when-let [resp @hal-response]
                      (-> (prev-page resp)
                          (.then (fn [new-resp] (reset! hal-response new-resp)))))]
        :.append
        [Gtk/Label :label "•"]
        :.append
        [Gtk/Button
         :halign Gtk/Align.END
         :label "Next"
         :sensitive (derived-atom [hal-response] :next-story-page #(and % (has-relation? % :next)))
         :$clicked #(when-let [resp @hal-response]
                      (-> (next-page resp)
                          (.then (fn [new-resp] (reset! hal-response new-resp)))))]]]]])) ; 6 closing brackets

(defn home-stories []
  (let [stack (Adw/ViewStack.)]
    (doto (.add_titled stack
                       (build-ui (stories-list-view "/feeds/lobsters/hot"))
                       "hottest"
                       "Hottest")
      (.set_icon_name "power-profile-performance-symbolic"))
    (doto (.add_titled stack
                       (build-ui (stories-list-view "/feeds/lobsters/newest"))
                       "active" "Active")
      (.set_icon_name "audio-speakers-symbolic"))
    (swap! state/global-widgets assoc :home-stories stack)
    [Gtk/Box
     ::rollui/ref-in [global-widgets :home]
     :orientation Gtk/Orientation.VERTICAL
     :.append
     stack]))
