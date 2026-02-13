(ns test.html2gtk.text2html-stream-test
  (:require [cljs.test :refer-macros [deftest is]]
            [html2gtk.text2html-stream :as t2h]))

(defn- make-stream [events]
  (t2h/make-stream
   #js {:startTag (fn [tag attrs]
                    (swap! events conj [:start tag (js->clj attrs)]))
        :endTag (fn [tag]
                  (swap! events conj [:end tag]))
        :text (fn [content]
                (swap! events conj [:text content]))}))

(deftest emits-nested-tag-events-in-order
  (let [events (atom [])
        stream (make-stream events)]
    (.writeChunk stream "<p>a <strong>b</strong></p>")
    (.end stream)
    (is (= [[:start "p" {}]
            [:text "a "]
            [:start "strong" {}]
            [:text "b"]
            [:end "strong"]
            [:end "p"]]
           @events))))

(deftest handles-chunk-split-tags
  (let [events (atom [])
        stream (make-stream events)]
    (.writeChunk stream "<stro")
    (.writeChunk stream "ng>hi</strong>")
    (.end stream)
    (is (= [[:start "strong" {}]
            [:text "hi"]
            [:end "strong"]]
           @events))))

(deftest decodes-entities-before-forwarding-text
  (let [events (atom [])
        stream (make-stream events)]
    (.writeChunk stream "Fish &amp; Chips &lt;b&gt;")
    (.end stream)
    (is (= [[:text "Fish & Chips <b>"]]
           @events))))

(deftest forwards-self-closing-tags
  (let [events (atom [])
        stream (make-stream events)]
    (.writeChunk stream "<p>x<br/>y<hr><img alt='a &amp; b'/></p>")
    (.end stream)
    (is (= [[:start "p" {}]
            [:text "x"]
            [:start "br" {}]
            [:end "br"]
            [:text "y"]
            [:start "hr" {}]
            [:end "hr"]
            [:start "img" {"alt" "a & b"}]
            [:end "img"]
            [:end "p"]]
           @events))))
