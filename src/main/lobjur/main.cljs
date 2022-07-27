(ns lobjur.main
  (:require
   [clojure.string :as str]
   [lobjur.widgets.window :refer [window-content]]
   [rollui.core :refer [build-ui]]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.byteArray" :as ByteArray]))
;;(reset! curr-view {:name :home})

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
      background: alpha(@yellow_2, 0.15);
      padding: 2px 4px;
      color: @theme_fg_color;
      border-radius: 8px;
      box-shadow: 0px 0px 0px 1px inset alpha(@yellow_4, 0.2);
  }
  .pill.round {
    padding: 4px 4px;
    margin: 4px;
    border-radius: 100%;
    box-shadow: 0px 0px 0px 1px inset alpha(@theme_fg_color, 0.3);
    min-height: 24px;
    min-width: 24px;
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
   600))
(defn ^:export main [& args]
  (println "Command line arguments are: " (str/join ", " args))
  (doto (Adw/Application. #js {:application_id "com.ranfdev.Lobjur"})
    (.connect "activate" activate)
    (.run #js [])))
