(ns html2gtk.text2html-stream
  (:require ["./Text2HtmlStream.js" :refer [Text2HtmlStream]]))

(defn make-stream [target]
  (Text2HtmlStream. target))
