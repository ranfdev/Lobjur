(ns lobjur.widgets.window
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]
   [lobjur.state :as state :refer [global-widgets]]
   [rollui.core :as rollui :refer [derived-atom]]))

(defn sidebar-header-bar []
  [Adw/HeaderBar
   :.pack_end [Adw/Bin
               :child (derived-atom [state/state]
                                    :sidebar-header-end
                                    #(get % :sidebar-header-end nil))]
   :title_widget [Adw/Bin
                  :child (derived-atom [state/state]
                                       :sidebar-header-title
                                       #(get % :sidebar-header-title
                                              [Adw/WindowTitle :title "Lobjur"]))]])

(defn content-header-bar []
  [Adw/HeaderBar
   :title_widget [Adw/Bin
                  :visible (derived-atom [state/state]
                                         :content-top-view-switcher
                                         #(and (get % :show-view-switcher false) (not (get % :mobile-bottom-view-switcher false))))
                   :child [Adw/ViewSwitcher
                          ::rollui/ref-in [global-widgets :content-view-switcher]
                          :policy Adw/ViewSwitcherPolicy.WIDE
                          :stack (rollui/derived-atom [global-widgets]
                                                      :content-stack
                                                      #(get @global-widgets :content-stack nil))]]
   :.pack_end [Adw/Bin
               :child (derived-atom [state/state]
                                    :content-header-end
                                    #(get % :content-header-end nil))]])

(defn window-content []
  [Adw/NavigationSplitView
   ::rollui/ref-in [global-widgets :split-view]
   :min-sidebar-width 300.0
   :max-sidebar-width 420.0
   :sidebar-width-fraction 0.35
   :sidebar
   [Adw/NavigationPage
    :title "Lobjur"
    :tag "sidebar"
    :child
    [Adw/ToolbarView
     :.add_top_bar (sidebar-header-bar)
     :.add_bottom_bar [Adw/ViewSwitcherBar
                       ::rollui/ref-in [global-widgets :sidebar-view-switcher-bar]
                       :reveal true]
     :content [Adw/NavigationView
               ::rollui/ref-in [global-widgets :sidebar-nav-view]
               :.add
               [Adw/NavigationPage
                :title "Home"
                :tag "sidebar-home"
                :child [Adw/Bin ::rollui/ref-in [global-widgets :sidebar-content-bin]]]]]]
   :content
   [Adw/NavigationPage
    :title " "
    :tag "content"
    :child
    [Adw/NavigationView
     ::rollui/ref-in [global-widgets :content-nav-view]
     :.add
     [Adw/NavigationPage
      :title "Story"
      :tag "content-root"
      :child
      [Adw/ToolbarView
       :.add_top_bar (content-header-bar)
       :.add_bottom_bar [Adw/ViewSwitcherBar
                         ::rollui/ref-in [global-widgets :content-view-switcher-bar]
                         :visible (derived-atom [state/state]
                                                :content-bottom-view-switcher
                                                #(and (get % :show-view-switcher false) (get % :mobile-bottom-view-switcher false)))
                         :reveal true]
       :content [Adw/Bin
                 ::rollui/ref-in [global-widgets :content-detail-bin]
                 :child [Adw/StatusPage
                         :icon-name "user-idle-symbolic"
                         :title "Select a story"
                         :description "Choose a story from the sidebar to read comments"]]]]]]])

