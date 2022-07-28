(ns lobjur.state)


(defn init-stories [kw]
  {:name :stories :stories-kw kw :page 1})
(defonce curr-view (atom (init-stories :hottest)))

;; To test a comment-heavy story, open a story, then evaluate this line
;; (swap! curr-view assoc-in [:story :short_id] "jclvos")
