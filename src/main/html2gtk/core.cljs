(ns html2gtk.core
  (:require ["gjs.gi.Gtk" :as Gtk]
            ["./Html2GtkStream.js" :refer [Html2GtkStream]]
            ["./Text2HtmlStream.js" :refer [Text2HtmlStream]]))

(defn make-stream
  ([root-widget] (make-stream root-widget nil))
  ([root-widget {:keys [on-link-activate]}]
   (Text2HtmlStream.
    (Html2GtkStream. root-widget #js {:onLinkActivate on-link-activate}))))

(defn make-root-widget []
  (doto (Gtk/Box. #js {:orientation Gtk/Orientation.VERTICAL
                       :spacing 4
                       :hexpand true})
    (.add_css_class "html-content")))

(defn render-html-widget
  ([html] (render-html-widget html nil))
  ([html opts]
   (let [root (make-root-widget)
         stream (make-stream root opts)]
    (.writeObject ^js stream (or html ""))
    (.end ^js stream)
    root)))
