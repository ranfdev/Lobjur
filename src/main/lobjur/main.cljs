(ns lobjur.main
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Gio" :as Gio]
   [clojure.string :as str]
   [lobjur.state :as state]
   [lobjur.widgets.comments :as comments]
    [lobjur.widgets.webview :as webview]
   [lobjur.widgets.stories-list-view :refer [home-stories stories-list-view]]
   [lobjur.widgets.user :as user]
   [lobjur.widgets.window :refer [window-content]]
   [api.router :as api]
   [api.helpers :refer [external-url provider-comments-url provider-user-url]]
   [api.sources :as sources]
   [rollui.core :refer [build-ui derived-atom]]))

(defn create-story-stack
  "Create a ViewStack with comments and web view pages for a story."
  [story]
  (let [comments-page (build-ui (comments/comments-view story))
        url (external-url story)
        has-url? (some? url)
        stack (Adw/ViewStack.)]
    ;; Add comments page with icon
    (doto (.add_titled stack comments-page "comments" "Comments")
      (.set_icon_name "user-available-symbolic"))
    ;; Add web view page if URL exists
    (when has-url?
      (doto (.add_titled stack (webview/webview-page url) "article" "Article")
        (.set_icon_name "web-browser-symbolic")))
    ;; Store stack reference in global-widgets and update view-switcher bar if present
    (swap! state/global-widgets assoc :content-stack stack)
    (when-let [bar (:content-view-switcher-bar @state/global-widgets)]
      (.set_stack ^js bar stack))
    (when-let [vs (:content-view-switcher @state/global-widgets)]
      (.set_stack ^js vs stack))
    stack))

(defn set-content-root-with-stack
  "Set content root with a stack of views (comments + optional web view)."
  [state story initial-view]
  (let [;; Clean up old stack/webview if exists
        old-stack (:content-stack @state/global-widgets)
        _ (when old-stack
            ;; GTK will handle cleanup when we replace the child
            ;; Clear the reference
            (swap! state/global-widgets dissoc :content-stack))
        stack (create-story-stack story)
        url (external-url story)
        has-url? (some? url)
        view-name (if (= initial-view :comments) "comments"
                    (if has-url? "article" "comments"))]
    (.set_child ^js (:content-detail-bin @state/global-widgets) stack)
    (.pop_to_tag ^js (:content-nav-view @state/global-widgets) "content-root")
    (.set_show_content ^js (:split-view @state/global-widgets) true)
    ;; Set visible child based on initial-view parameter
    (.set_visible_child_name ^js stack view-name)
    (assoc state :selected-story-view stack)))


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
           (let [sidebar-view (build-ui (home-stories))
                 initial-source (first sources/sources)
                 search-href (get-in initial-source [:extra-links :search])]
             (.set_child ^js (:sidebar-content-bin @state/global-widgets) sidebar-view)
             (-> state
                 (assoc :sidebar-header-title (:home-dropdown @state/global-widgets))
                 (assoc :search-href search-href)
                 (assoc :sidebar-header-start
                        [Gtk/Button
                         :icon-name "edit-find-symbolic"
                         :tooltip-text "Search"
                         :visible (derived-atom [state/state] :search-visible
                                    #(some? (:search-href %)))
                         :$clicked #(state/send [:push-search])])
                 (assoc :sidebar-header-end [Gtk/MenuButton
                                             :icon-name "open-menu-symbolic"
                                             :tooltip-text "Main menu"
                                             :menu-model (doto (Gio/Menu.)
                                                           (.append "Reload" "win.reload")
                                                           (.append "About" "win.about")
                                                           (.append "Donate" "win.donate"))])))

           :reload
           (let [sidebar-view (build-ui (home-stories))]
             (.set_child ^js (:sidebar-content-bin @state/global-widgets) sidebar-view)
             state)

           :select-story
            (let [url (external-url payload)
                  has-url? (some? url)
                  comments-url (provider-comments-url payload)
                  initial-view (get payload :initial-view (if has-url? :article :comments))]
              (-> state
                  (set-content-root-with-stack payload initial-view)
                  (assoc :selected-story payload)
                  (assoc :show-view-switcher has-url?) ; Hide switcher if no URL
                  (assoc :content-header-end
                         [Gtk/MenuButton
                          :tooltip-text "Story options"
                          :icon-name "view-more-symbolic"
                          :css_classes #js ["flat"]
                          :menu-model (let [menu (Gio/Menu.)]
                                        (when has-url?
                                          (.append menu "Copy Article Link" "win.copy-article-link"))
                                        (when comments-url
                                          (.append menu "Copy Comments Link" "win.copy-comments-link"))
                                        (when has-url?
                                          (.append menu "Open Externally" "win.open-externally"))
                                        menu)])))


            :push-user
           (let [{:keys [href title]} payload
                 provider (cond
                            (str/includes? href "lobsters") "lobsters"
                            (str/includes? href "hackernews") "hackernews"
                            :else nil)
                 profile-url (when provider
                               (provider-user-url {:provider provider :username title}))
                 menu (when profile-url
                        (doto (Gio/Menu.)
                          (.append "Copy Profile Link" "win.copy-user-link")
                          (.append "Open Profile" "win.open-user-externally")))
                 page (Adw/NavigationPage.
                       #js {:child (build-ui
                                    [Adw/ToolbarView
                                     :.add_top_bar [Adw/HeaderBar
                                                    :.pack_end (when menu
                                                                 [Gtk/MenuButton
                                                                  :icon-name "view-more-symbolic"
                                                                  :tooltip-text "User options"
                                                                  :css_classes #js ["flat"]
                                                                  :menu-model menu])]
                                     :content (user/user-view href)])
                            :title title})]
             (.push ^js (:content-nav-view @state/global-widgets) page)
             (assoc state :selected-user {:username title :provider provider}))

           :push-user-stories
           (let [{:keys [href title]} payload]
             (push-sidebar-page state (stories-list-view href) title))

           :push-domain-stories
           (let [{:keys [href title]} payload]
             (push-sidebar-page state (stories-list-view href) title))

           :push-tagged-stories
           (let [{:keys [href title]} payload]
             (push-sidebar-page state (stories-list-view href) title))

           :push-search
           (let [results-bin (doto (Adw/Bin.) (.set_vexpand true))
                 on-search (fn [entry]
                             (let [q (.get_text ^js entry)]
                               (when (not-empty q)
                                 (.set_child results-bin
                                   (build-ui (stories-list-view
                                               (str "/hackernews/search?q=" q)))))))]
             (push-sidebar-page state
               [Gtk/Box
                :orientation Gtk/Orientation.VERTICAL
                :.append [Gtk/SearchEntry
                          :placeholder-text "Search Hacker News…"
                          :hexpand true
                          :margin-start 8
                          :margin-end 8
                          :margin-top 8
                          :$activate on-search]
                :.append results-bin]
               "Search"))

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
   .comment-collapse-btn {
      padding: 0px 0px;
      min-height: 14px;
      min-width: 14px;
   }
   .comment {
     border-left: 2px solid alpha(@theme_fg_color, 0.4);
     border-radius: 4px;
   }
  .comment-depth-0 { border-left-color: @blue_3; }
  .comment-depth-1 { border-left-color: @green_3; }
  .comment-depth-2 { border-left-color: @yellow_3; }
  .comment-depth-3 { border-left-color: @orange_3; }
  .comment-depth-4 { border-left-color: @red_3; }
  .comment-depth-5 { border-left-color: @purple_3; }
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
                 720.0
                 Adw/LengthUnit.SP))
          split-view (:split-view @state/global-widgets)]
      (.connect bp "apply" (fn [_]
                             (.set_collapsed ^js split-view true)
                             (swap! state/state assoc :mobile true)))
      (.connect bp "unapply" (fn [_]
                               (.set_collapsed ^js split-view false)
                               (swap! state/state assoc :mobile false)))
      (.add_breakpoint win bp))
    (println "Window created:" win)
    (doto win
      (.present)
      (.add_action (doto (Gio/SimpleAction. #js {:name "about"})
                     (.connect "activate" #(about))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "donate"})
                     (.connect "activate" #(Gtk/show_uri nil "https://github.com/sponsors/ranfdev" 0))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "reload"})
                     (.connect "activate" (fn [_ _] (state/send [:reload])))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "copy-article-link"})
                     (.connect "activate" (fn [_ _]
                                            (when-let [url (external-url (:selected-story @state/state))]
                                              (let [clipboard (.get_clipboard (Gdk/Display.get_default))]
                                                (.set ^js clipboard url)))))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "copy-comments-link"})
                     (.connect "activate" (fn [_ _]
                                            (when-let [url (provider-comments-url (:selected-story @state/state))]
                                              (let [clipboard (.get_clipboard (Gdk/Display.get_default))]
                                                (.set ^js clipboard url)))))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "open-externally"})
                     (.connect "activate" (fn [_ _]
                                            (when-let [url (external-url (:selected-story @state/state))]
                                              (Gtk/show_uri nil url 0))))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "copy-user-link"})
                     (.connect "activate" (fn [_ _]
                                            (when-let [{:keys [username provider]} (:selected-user @state/state)]
                                              (when-let [url (provider-user-url {:username username :provider provider})]
                                                (let [clipboard (.get_clipboard (Gdk/Display.get_default))]
                                                  (.set ^js clipboard url))))))))
      (.add_action (doto (Gio/SimpleAction. #js {:name "open-user-externally"})
                     (.connect "activate" (fn [_ _]
                                            (when-let [{:keys [username provider]} (:selected-user @state/state)]
                                              (when-let [url (provider-user-url {:username username :provider provider})]
                                                (Gtk/show_uri nil url 0))))))))
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
