(ns lobjur.utils.common
  (:require [clojure.string :as str]))

(defn parse-json [t] (-> t (js/JSON.parse) (js->clj :keywordize-keys true)))

(defn html->text
  "Convert HTML to plain text by converting structural tags to newlines,
   stripping remaining tags, and decoding common HTML entities."
  [html]
  (when html
    (-> html
        (str/replace #"<br\s*/?>" "\n")
        (str/replace #"</p>" "\n\n")
        (str/replace #"<p>" "")
        (str/replace #"<[^>]*>" "")
        (str/replace "&amp;" "&")
        (str/replace "&lt;" "<")
        (str/replace "&gt;" ">")
        (str/replace "&quot;" "\"")
        (str/replace "&#x27;" "'")
        (str/replace "&#x2F;" "/")
        (str/replace "&nbsp;" " ")
        (str/replace #"\n{3,}" "\n\n")
        (str/trim))))

