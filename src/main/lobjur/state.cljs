(ns lobjur.state)

(def global-widgets (atom {}))

(def transducers (atom []))
(defn add-transducer [r]
  (swap! transducers conj r))

(def state (atom nil))
(defn reduce-state [state action]
  (transduce
   (apply comp @transducers)
   (fn [state _] state)
   state
   [action]))

(defn send [action]
  (reset! state (reduce-state @state action)))

; # REPL helpers
; (:prev-state @state)
; (send [:pop-main-stack])
; To test a comment-heavy story, open a story, then evaluate this line
; (-> (lobster/story "jclvos")
;    (.then #(send [:push-story %])))
