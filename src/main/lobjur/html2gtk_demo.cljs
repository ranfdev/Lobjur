(ns lobjur.html2gtk-demo
  (:require ["gjs.gi.Adw" :as Adw]
            ["gjs.gi.Gtk" :as Gtk]
            [html2gtk.core :as h2g]))

(def demo-html
  "<main>
     <h1>Html2Gtk Widget Demo</h1>
     <p>This demo renders all supported Html2Gtk widgets and inline styles.</p>
     <hr />
     <article>
       <h2>Headings</h2>
       <h3>Heading level 3</h3>
       <h4>Heading level 4</h4>
       <h5>Heading level 5</h5>
       <h6>Heading level 6</h6>
     </article>
     <section>
       <div>
         <p>Inline tags: <strong>strong</strong>, <b>bold</b>, <em>emphasis</em>, <i>italic</i>, <u>underline</u>, <s>strikethrough</s>, <code>inline code</code>, <span>span text</span>, <a href='https://lobste.rs'>link</a>.</p>
         <p>Line break demo<br/>Second line after br.</p>
       </div>
     </section>
     <blockquote>
       <p>This is a blockquote with <em>styled</em> content.</p>
     </blockquote>
     <pre><code>(println \"preformatted code\")\n(map inc [1 2 3])</code></pre>
     <p>Image fallback: <img src='https://example.com/image.png' alt='[demo image alt text]' /></p>
     <h2>Lists</h2>
     <ul>
       <li>Unordered item 1</li>
       <li>Unordered item 2 with <code>code</code></li>
     </ul>
     <ol>
       <li>Ordered item 1</li>
       <li>Ordered item 2</li>
     </ol>
   </main>")

(defn activate [app]
  (let [content (h2g/render-html-widget demo-html)
        scroller (Gtk/ScrolledWindow. #js {:vexpand true
                                           :hexpand true
                                           :child content})
        win (Adw/ApplicationWindow. #js {:application app
                                         :title "Html2Gtk Demo"
                                         :default_width 840
                                         :default_height 640
                                         :content scroller})]
    (.present win)))

(defn ^:export main [& _args]
  (doto (Adw/Application. #js {:application_id "com.ranfdev.Lobjur.Html2GtkDemo"})
    (.connect "activate" activate)
    (.run #js [])))
