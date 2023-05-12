(ns lobjur.main
  (:require
   ["gjs.byteArray" :as ByteArray]
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Gio" :as Gio]
   [lobjur.state :as state]
   [lobjur.widgets.comments :as comments]
   [lobjur.widgets.stories-list-view :refer [home-stories stories-list-view]]
   [lobjur.widgets.user :as user]
   [lobjur.widgets.window :refer [window-content]]
   [lobster.core :as lobster]
   [rollui.core :refer [build-ui]]))

(def back-btn [Gtk/Button
               :$clicked #(state/send [:pop-main-stack])
               :icon-name "go-previous-symbolic"])

(defn push-view [s view]
  (let [v (build-ui view)]
    (doto ^js (:main-stack @state/global-widgets)
      (.add_child v)
      (.set_visible_child v))
    (merge s
           {:header-start nil
            :header-end nil
            :prev-state s
            :curr-view v})))

(defn push-titled-view [s view title]
  (-> s
      (push-view view)
      (assoc :header-start back-btn)
      (assoc :title-widget [Gtk/Label :label title])))
(defn app-transducer [f]
  (fn
    ([s] s)
    ([state [k payload :as action]]
     (-> (case k
           :init
           (-> state
               (push-view (home-stories))
               (assoc :header-end [Gtk/MenuButton
                                   :icon-name "open-menu-symbolic"
                                   :menu-model (doto (Gio/Menu.)
                                                 (.append "About" "win.about")
                                                 (.append "Donate" "win.donate"))])
               (assoc :title-widget
                      [Adw/ViewSwitcher
                       :policy Adw/ViewSwitcherPolicy.WIDE
                       :stack (:home-stories @state/global-widgets)]))
           :push-user
           (push-titled-view state (user/user-view payload) payload)
           :push-user-stories
           (push-titled-view state
                             (stories-list-view (partial lobster/user-stories-newest payload))
                             payload)
           :push-domain-stories
           (push-titled-view state
                             (stories-list-view (partial lobster/domain-stories payload))
                             payload)
           :push-story
           (-> state
               (push-titled-view (comments/comments-view payload) "Comments")
               (assoc :header-end
                      [Gtk/LinkButton
                       :uri (lobster/rel "s/" (:short_id payload))
                       :icon-name "web-browser-symbolic"
                       :css_classes #js ["image-button"]]))
           :push-tagged-stories
           (push-titled-view state
                             (stories-list-view (partial lobster/tagged payload))
                             payload)
           :pop-main-stack
           (let [prev-state (:prev-state state)]
             (doto ^js (:main-stack @state/global-widgets)
               (.set_visible_child (:curr-view prev-state))
               (.remove (:curr-view state)))
             (merge state
                    ;; Reset these keys to the ones in the previous state
                    (select-keys
                     prev-state
                     [:prev-state :curr-view :title-widget :header-start :header-end]))))

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
               :default_width 720
               :default_height 720
               :content
               (build-ui (window-content))})]
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
