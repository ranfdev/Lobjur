(ns rollui.core)

(defmacro defc [ident _ body]
  (let [x (symbol (str ident "___hot_reload_atom"))
        y (symbol (str ident "___hot_reload_body"))]
    `(do
       (defn ~y [] ~body)
       (defonce ~x (atom nil))
       (reset! ~x (~y)) ;; re-evaluate the body on reload
       (defn ~ident []
         (reset! ~x (~y)) ;; re-evaluate the body on function call, as usual
         [Adw/Bin :child ~x]))))
