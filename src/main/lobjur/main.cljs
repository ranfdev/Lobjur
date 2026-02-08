(ns lobjur.main
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Gio" :as Gio]
   [lobjur.state :as state]
   [lobjur.widgets.comments :as comments]
   [lobjur.widgets.stories-list-view :refer [home-stories stories-list-view]]
   [lobjur.widgets.user :as user]
   [lobjur.widgets.window :refer [window-content]]
   [api.router :as api]
   [api.helpers :refer [external-url]]
   [rollui.core :refer [build-ui]]))

(defn set-content-root [state view]
  (let [w (build-ui view)]
    (.set_child ^js (:content-detail-bin @state/global-widgets) w)
    (.pop_to_tag ^js (:content-nav-view @state/global-widgets) "content-root")
    (.set_show_content ^js (:split-view @state/global-widgets) true)
    (assoc state :selected-story-view w)))

(defn push-sidebar-page [state view title]
  (let [page (Adw/NavigationPage.
              #js {:child (build-ui
                           [Adw/ToolbarView
                            :.add_top_bar [Adw/HeaderBar]
                            :content view])
                   :title title})]
    (.push ^js (:sidebar-nav-view @state/global-widgets) page)
    (.set_show_content ^js (:split-view @state/global-widgets) false)
    state))

(defn push-content-page [state view title]
  (let [page (Adw/NavigationPage.
              #js {:child (build-ui
                           [Adw/ToolbarView
                            :.add_top_bar [Adw/HeaderBar]
                            :content view])
                   :title title})]
    (.push ^js (:content-nav-view @state/global-widgets) page)
    state))

(defn app-transducer [f]
  (fn
    ([s] s)
    ([state [k payload :as action]]
     (-> (case k
           :init
           (let [sidebar-view (build-ui (home-stories))]
             (.set_child ^js (:sidebar-content-bin @state/global-widgets) sidebar-view)
             (-> state
                 (assoc :sidebar-header-start (:home-dropdown @state/global-widgets))
                 (assoc :sidebar-header-end [Gtk/MenuButton
                                             :icon-name "open-menu-symbolic"
                                             :menu-model (doto (Gio/Menu.)
                                                           (.append "About" "win.about")
                                                           (.append "Donate" "win.donate"))])
                 (assoc :sidebar-title-widget
                        (:home-view-switcher @state/global-widgets))))

           :select-story
           (-> state
               (set-content-root (comments/comments-view payload))
               (assoc :selected-story payload)
               (assoc :content-header-end
                      (when-let [url (external-url payload)]
                        [Gtk/LinkButton
                         :uri url
                         :icon-name "web-browser-symbolic"
                         :css_classes #js ["image-button"]])))

           :push-user
           (push-content-page state (user/user-view payload) payload)

           :push-user-stories
           (push-sidebar-page state
                              (stories-list-view (str "/users/" payload "/stories"))
                              (str payload "'s Stories"))
           :push-domain-stories
           (push-sidebar-page state
                              (stories-list-view (str "/domains/" payload "/stories"))
                              (str payload " Stories"))
           :push-tagged-stories
           (push-sidebar-page state
                              (stories-list-view (str "/tags/" payload "/stories?source=lobsters"))
                              (str payload " Stories"))

           :pop-main-stack state)

         (f action)))))
(state/add-transducer app-transducer)

(def app-css
  ".small.button {
      padding: 0px 8px;
   }
  .comment-revealer-btn {
     padding: 2px 2px;
     min-height: 16px;
     min-width: 16px;
  }
  .comment {
    border-left: 2px solid alpha(@theme_fg_color, 0.4);
    border-radius: 4px;
  }
  .tag {
      min-height: 16px;
      min-width: 16px;
      background: alpha(@yellow_2, 0.15);
      padding: 2px 4px;
      color: @theme_fg_color;
      border-radius: 8px;
      box-shadow: 0px 0px 0px 1px inset alpha(@yellow_4, 0.2);
  }
  .tag:hover {
      background: alpha(@yellow_4, 0.2);
  }
  ")

(defn about []
  (.present (build-ui
             [Adw/AboutWindow
              :license "
                       This program comes with absolutely no warranty.
                       See the <a href=\"https://www.gnu.org/licenses/gpl-3.0.html\">GNU General Public License, version 3 or later</a> for details."
              :application-name "Lobjur"
              :application-icon "com.ranfdev.Lobjur"
              :authors #js ["ranfdev https://ranfdev.com/about"]
              :version "1.3.0"
              :comments "A simple https://lobste.rs client"
              :license-type Gtk/License.GPL_3_0
              :website-label "Source"
              :website "https://github.com/ranfdev/Lobjur"])))

(defn activate [app]
  (let [win (Adw/ApplicationWindow.
             #js
              {:application app
               :default_width 900
               :default_height 720
               :content
               (build-ui (window-content))})]
    ;; Add breakpoint: collapse split-view on narrow windows
    (let [bp (Adw/Breakpoint.new
               (Adw/BreakpointCondition.new_length
                 Adw/BreakpointConditionLengthType.MAX_WIDTH
                 500.0
                 Adw/LengthUnit.SP))
          split-view (:split-view @state/global-widgets)]
      (.connect bp "apply" (fn [_]
                             (.set_collapsed ^js split-view true)
                             (when-let [bar (:sidebar-view-switcher-bar @state/global-widgets)]
                               (.set_reveal ^js bar true))
                             (swap! state/state assoc :sidebar-title-widget nil)))
      (.connect bp "unapply" (fn [_]
                               (.set_collapsed ^js split-view false)
                               (when-let [bar (:sidebar-view-switcher-bar @state/global-widgets)]
                                 (.set_reveal ^js bar false))
                               (swap! state/state assoc :sidebar-title-widget
                                      (:home-view-switcher @state/global-widgets))))
      (.add_breakpoint win bp))
    (println "Window created:" win)
    (doto win
      (.present)
      (.add_action (doto (Gio/SimpleAction. #js {:name "about"})
                     (.connect "activate" #(about))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "donate"})
                     (.connect "activate" #(Gtk/show_uri nil "https://github.com/sponsors/ranfdev" 0)))))
    (.present win))
  (Gtk/StyleContext.add_provider_for_display
   (Gdk/Display.get_default)
   (doto (new Gtk/CssProvider) (.load_from_data app-css -1))
   600)
  (state/send [:init]))

(defn ^:export main [& _args]
  (doto (Adw/Application. #js {:application_id "com.ranfdev.Lobjur"})
    (.connect "activate" activate)
    (.run #js [])))
