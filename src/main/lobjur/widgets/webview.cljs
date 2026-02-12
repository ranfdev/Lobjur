(ns lobjur.widgets.webview
  (:require
   ["gjs.gi.WebKit" :as WebKit]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Adw" :as Adw]
   [rollui.core :refer [build-ui]]))

(defn webview-widget
  "Create a WebKit WebView widget.
   Returns a widget (either the WebView directly or wrapped in a container)."
  []
  (let [webview (WebKit/WebView.)]
    ;; WebView is scrollable by itself, no need for ScrolledWindow
    (doto ^js webview
      (.set_vexpand true)
      (.set_hexpand true))
    webview))

(defn load-url
  "Load a URL in the given WebView widget."
  [webview url]
  (.load_uri ^js webview url))

(defn webview-page
  "Create a complete web view page with loading indicator and error handling.
   Returns a widget showing the web view with loading feedback and error recovery."
  [url]
  (let [webview (webview-widget)
        loading-active? (atom false)
        loading-bar (doto (Gtk/ProgressBar.)
                      (.set_show_text false)
                      (.set_hexpand true)
                      (.set_halign Gtk/Align.FILL)
                      (.set_valign Gtk/Align.START)
                      (.set_visible false)
                      (.set_can_target false)
                      (.set_size_request -1 2))
        stack (Gtk/Stack.)
        error-page (build-ui
                    [Adw/StatusPage
                     :icon-name "dialog-error-symbolic"
                     :title "Failed to Load Page"
                     :description "The page could not be loaded. Check your connection and try again."
                     :child [Gtk/Button
                             :label "Retry"
                             :halign Gtk/Align.CENTER
                             :.add_css_class "pill"
                             :.add_css_class "suggested-action"
                             :$clicked (fn [_]
                                         (.set_visible_child_name stack "content")
                                         (doto ^js loading-bar
                                           (.set_fraction 0.0)
                                           (.set_visible true))
                                         (load-url webview url))]])
        overlay (build-ui
                 [Gtk/Overlay
                   :child stack
                   :.add_overlay loading-bar])]
    
    ;; Add webview and error page to stack
    (.add_named stack webview "content")
    (.add_named stack error-page "error")
    (.set_visible_child_name stack "content")
    
    ;; Update loading indicator
    (.connect ^js webview "notify::estimated-load-progress"
              (fn [_ _]
                (when @loading-active?
                  (let [progress (.get_estimated_load_progress ^js webview)
                        fraction (-> progress (max 0.0) (min 1.0))]
                    (.set_fraction ^js loading-bar fraction)
                    (when (< fraction 1.0)
                      (.set_visible ^js loading-bar true))))))

    (.connect ^js webview "load-changed"
              (fn [_ load-event]
                (case load-event
                  WebKit/LoadEvent.STARTED
                  (do
                    (reset! loading-active? true)
                    (doto ^js loading-bar
                      (.set_fraction 0.0)
                      (.set_visible true)))
                  WebKit/LoadEvent.FINISHED
                  (do
                    (reset! loading-active? false)
                    (doto ^js loading-bar
                      (.set_fraction 1.0)
                      (.set_visible false)))
                  nil)))
    
    ;; Show error page if load fails
    (.connect ^js webview "load-failed"
              (fn [_ load-event error uri]
                (reset! loading-active? false)
                (.set_visible ^js loading-bar false)
                (.set_visible_child_name stack "error")
                true)) ;; Return true to stop error propagation
    
    ;; Load the URL
    (load-url webview url)
    overlay))
