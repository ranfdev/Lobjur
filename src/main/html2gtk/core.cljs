(ns html2gtk.core
  (:require ["gjs.gi.Gtk" :as Gtk]
            ["./Html2GtkStream.js" :refer [Html2GtkStream]]
            ["./Text2HtmlStream.js" :refer [Text2HtmlStream]]))

(defn make-stream [root-widget]
  (Text2HtmlStream. (Html2GtkStream. root-widget)))

(defn make-root-widget []
  (doto (Gtk/Box. #js {:orientation Gtk/Orientation.VERTICAL
                       :spacing 4
                       :hexpand true})
    (.add_css_class "html-content")))

(defn render-html-widget [html]
  (let [root (make-root-widget)
        stream (make-stream root)]
    (.writeObject ^js stream (or html ""))
    (.end ^js stream)
    root))
