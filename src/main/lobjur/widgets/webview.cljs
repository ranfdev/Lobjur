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
        spinner (build-ui [Adw/Spinner :spinning true])
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
                                        (.set_visible error-page false)
                                        (.set_visible spinner true)
                                        (load-url webview url))]])
        stack (Gtk/Stack.)
        overlay (build-ui
                 [Gtk/Overlay
                  :child stack
                  :.add_overlay
                  [Gtk/Box
                   :halign Gtk/Align.CENTER
                   :valign Gtk/Align.CENTER
                   :css_classes #js ["toolbar"]
                   :.append spinner]])]
    
    ;; Add webview and error page to stack
    (.add_named stack webview "content")
    (.add_named stack error-page "error")
    (.set_visible_child_name stack "content")
    
    ;; Hide spinner when page loads
    (.connect ^js webview "load-changed"
              (fn [_ load-event]
                (when (= load-event WebKit/LoadEvent.FINISHED)
                  (.set_visible ^js spinner false))))
    
    ;; Show error page if load fails
    (.connect ^js webview "load-failed"
              (fn [_ load-event error uri]
                (.set_visible ^js spinner false)
                (.set_visible_child_name stack "error")
                true)) ;; Return true to stop error propagation
    
    ;; Load the URL
    (load-url webview url)
    overlay))
