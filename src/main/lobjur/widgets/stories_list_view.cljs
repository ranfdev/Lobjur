(ns lobjur.widgets.stories-list-view
  (:require
   [lobjur.widgets.shared :refer [upvote-btn back-to-home-btn]]
   [lobjur.state :as state :refer [curr-view]]
   [lobjur.utils.http :as http]
   [lobjur.utils.common :refer [parse-json base-url-lobster]]
   [rollui.core :as rollui :refer-macros [defc]]
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.GLib" :as GLib]
   ["gjs.gi.Gtk" :as Gtk]))

(def stories-urls
  {:active "https://lobste.rs/active.json"
   :hottest "https://lobste.rs/hottest.json"
   :recents "https://lobste.rs/recents.json"})

(defn tagged-stories-url [tag page]
  (str "https://lobste.rs/t/" tag ".json" "?page=" page))
(defn compute-stories-url [kw page]
  (str (kw stories-urls) "?page=" page))


;;taken from https://stackoverflow.com/a/69122877/11189772
(js* "function timeAgo(input) {
  const date = (input instanceof Date) ? input : new Date(input);
  const formatter = new Intl.RelativeTimeFormat('en');
  const ranges = {
    years: 3600 * 24 * 365,
    months: 3600 * 24 * 30,
    weeks: 3600 * 24 * 7,
    days: 3600 * 24,
    hours: 3600,
    minutes: 60,
    seconds: 1
  };
  const secondsElapsed = (date.getTime() - Date.now()) / 1000;
  for (let key in ranges) {
    if (ranges[key] < Math.abs(secondsElapsed)) {
      const delta = secondsElapsed / ranges[key];
      return formatter.format(Math.round(delta), key);
    }
  }
}
global.timeAgo = timeAgo")
(defn story-item-widget
  [{:keys [short_id title url score created_at comment_count comments_url tags] :as story
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
       (let [host (.get_host (.parse_relative base-url-lobster url GLib/UriFlags.NONE))]
         [Gtk/LinkButton
          :css_classes #js ["small" "button" "flat" "caption"]
          :halign Gtk/Align.START
          :uri (str "https://lobste.rs/domain/" host) #_("Meh, not sure about this")
          :label host])
       :.append
       (for [t tags]
         [Gtk/Button
          :$clicked #(reset! curr-view {:name :tag
                                        :tag t
                                        :header-start back-to-home-btn
                                        :prev-state @curr-view
                                        :page 1})
          :label t
          :valign Gtk/Align.CENTER
          :css_classes #js ["small" "button" "flat" "tag" "caption"]])]
      [Gtk/Box
       :spacing 2
       :halign Gtk/Align.START
       :.append
       (list
        [Gtk/LinkButton
         :uri (str "https://lobste.rs/u/" username)
         :label username
         :css_classes #js ["small" "button" "flat" "body"]]
        [Gtk/Label
         :label (js/global.timeAgo created_at)])])]
    [Gtk/Button
     :valign Gtk/Align.CENTER
     :css_classes #js ["button" "flat"]
     :$clicked #(reset! curr-view {:name :comments
                                   :story story
                                   :header-start back-to-home-btn
                                   :prev-state @curr-view})
     :child
     [Gtk/Overlay
      :child
      [Gtk/Image :pixel_size 28 :opacity 0.5 :icon_name "user-idle-symbolic"]
      :.add_overlay [Gtk/Label
                     :css_classes #js ["caption-heading" "numeric"]
                     :label (str comment_count)]]])])

(declare top-bar)
(defc top-bar []
  [Gtk/ScrolledWindow
   :propagate_natural_width true
   :halign Gtk/Align.CENTER
   :child
   [Gtk/Box
    :valign Gtk/Align.CENTER
    :spacing 8
    :.append
    (list
     [Gtk/ToggleButton
      :label "Hottest"
      :active (= :hottest (:stories-kw @curr-view))
      :.add_css_class (list "small" "flat")
      :$clicked #(reset! curr-view (state/init-stories :hottest))]
     [Gtk/ToggleButton
      :label "Active"
      :active (= :active (:stories-kw @curr-view))
      :.add_css_class (list "small" "flat")
      :$clicked #(reset! curr-view (state/init-stories :active))]
     ;; /recent.json currently returns status 500
     ;; See https://github.com/lobsters/lobsters/issues/1114
     #_[Gtk/Button
        :label "Recent"
        :.add_css_class (list "small" "flat")
        :$clicked #(reset! curr-view (state/init-stories :recent))]
     ;; /search.json currently returns status 500
     ;; See https://github.com/lobsters/lobsters/issues/1115
     #_[Gtk/Button
        :label "Search"
        :.add_css_class (list "small" "flat")
        :$clicked #(reset! curr-view {:name :search})])]])

(defn stories-widget-provider [url]
  (-> (http/get url)
      (.then parse-json)
      (.then
       (partial map story-item-widget))
      (.catch println)))

(defn stories-list-view [top-bar url]
  [Gtk/ScrolledWindow
   :propagate_natural_height true
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
     (if (nil? top-bar)
       ::rollui/abort
       (top-bar))
     :.append
     [Gtk/ListBox
      :css_classes #js ["boxed-list"]
      :.append (stories-widget-provider url)]
     :.append
     [Gtk/Box
      :hexpand true
      :homogeneous true
      :.append
      [Gtk/Button
       :halign Gtk/Align.START
       :label "Previous"
       :sensitive (> (:page @curr-view) 1)
       :$clicked #(swap! curr-view update :page dec)]
      :.append
      [Gtk/Label :label (str "Page " (:page @curr-view))]
      :.append
      [Gtk/Button
       :halign Gtk/Align.END
       :label "Next"
       :$clicked #(swap! curr-view update :page inc)]]]]])

