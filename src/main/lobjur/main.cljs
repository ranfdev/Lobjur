(ns lobjur.main
  (:require
   ["gjs.byteArray" :as ByteArray]
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gtk" :as Gtk]
   [clojure.string :as str]
   [lobjur.state :as state]
   [lobjur.widgets.comments :as comments]
   [lobjur.widgets.stories-list-view :as stories-list-view :refer [home-stories]]
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
    (-> s
        (assoc :prev-state s)
        (assoc :curr-view v))))
(defn app-transducer [f]
  (fn
    ([s] s)
    ([state [k payload :as action]]
     (-> (case k
           :init
           (-> state
               (push-view (home-stories))
               (assoc :title-widget
                      [Gtk/StackSwitcher
                       :stack (:home-stories @state/global-widgets)]))
           :push-user
           (-> state
               (push-view (user/user-view payload))
               (assoc :header-start back-btn)
               (assoc :title-widget [Gtk/Label :label payload]))
           :push-user-stories
           (-> state
               (push-view
                (stories-list-view/stories-list-view (partial lobster/user-stories-newest payload)))
               (assoc :header-start back-btn)
               (assoc :title-widget [Gtk/Label :label payload]))
           :push-story
           (-> state
               (push-view (comments/comments-view payload))
               (assoc :title-widget [Gtk/Label :label "Comments"])
               (assoc :header-start back-btn))
           :push-tagged-stories
           (-> state
               (push-view (stories-list-view/stories-list-view (partial lobster/tagged payload)))
               (assoc :title-widget [Gtk/Label :label payload])
               (assoc :header-start back-btn))
           :pop-main-stack
           (do
             (doto ^js (:main-stack @state/global-widgets)
               (.set_visible_child (:curr-view (:prev-state state)))
               (.remove (:curr-view state)))
             (-> state
                 (assoc :prev-state (get-in state [:prev-state :prev-state]))
                 (assoc :curr-view (:curr-view (:prev-state state)))
                 (assoc :title-widget (:title-widget (:prev-state state)))
                 (assoc :header-start (if (get-in state [:prev-state :prev-state]) back-btn nil)))))
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

(defn activate [app]
  (doto (Adw/ApplicationWindow.
         #js
          {:application app
           :default_width 720
           :default_height 720
           :content
           (build-ui (window-content))})
    (.present))
  (Gtk/StyleContext.add_provider_for_display
   (Gdk/Display.get_default)
   (doto (new Gtk/CssProvider) (.load_from_data (ByteArray/fromString app-css)))
   600)
  (state/send [:init]))

(defn ^:export main [& args]
  (println "Command line arguments are: " (str/join ", " args))
  (doto (Adw/Application. #js {:application_id "com.ranfdev.Lobjur"})
    (.connect "activate" activate)
    (.run #js [])))
