(ns html2gtk.styles)

(def html2gtk-css
  ".html-paragraph {
      margin-bottom: 4px;
   }
   .html-blockquote {
      border-left: 3px solid alpha(@theme_fg_color, 0.3);
      padding-left: 8px;
      margin: 4px 0;
   }
   .html-pre {
      background: alpha(@theme_fg_color, 0.08);
      border-radius: 6px;
      padding: 6px;
      margin: 0;
   }
   .html-pre-scroller {
      margin: 4px 0;
   }
   .html-pre-text, .html-inline-code {
      font-family: monospace;
   }
   .html-inline-code {
      background: alpha(@theme_fg_color, 0.08);
      border-radius: 4px;
      padding: 0 4px;
   }
   .html-strong {
      font-weight: 700;
   }
   .html-emphasis {
      font-style: italic;
   }
   .html-underline {
      text-decoration: underline;
   }
   .html-strikethrough {
      text-decoration: line-through;
   }
   .html-list-item {
      margin: 1px 0;
   }
   .html-heading.h1 { font-size: 1.80em; font-weight: 700; }
   .html-heading.h2 { font-size: 1.55em; font-weight: 700; }
   .html-heading.h3 { font-size: 1.35em; font-weight: 700; }
   .html-heading.h4 { font-size: 1.20em; font-weight: 700; }
   .html-heading.h5 { font-size: 1.08em; font-weight: 700; }
   .html-heading.h6 { font-size: 0.98em; font-weight: 700; }
   .html-content link:hover {
      text-decoration: underline;
      opacity: 0.9;
   }")

(def demo-css
  (str ".demo-html2gtk-window headerbar {
         box-shadow: inset 0 -1px alpha(@theme_fg_color, 0.12);
      }
      .demo-html2gtk-window .title {
         font-weight: 600;
      }
      .demo-html-root {
         padding: 16px;
      }
      .demo-html-root .html-heading {
         margin-top: 10px;
         margin-bottom: 4px;
      }
      "
       html2gtk-css))
