(ns lobjur.utils.common
  )

(defn parse-json [t] (-> t (js/JSON.parse) (js->clj :keywordize-keys true)))

