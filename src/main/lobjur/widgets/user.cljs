(ns lobjur.widgets.user
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]
   ["gjs.gi.Pango" :as Pango]
   [lobjur.utils.common :refer [parse-json]]
   [lobster.core :as lobster]))

(defn user-view [username]
  [Adw/Clamp
   :margin-start 8
   :margin-end 8
   :margin-top 8
   :margin-bottom 8
   :child
   [Gtk/ScrolledWindow
    :propagate-natural-height true
    :child
    (-> (lobster/user username)
        (.then
         (fn [user]
           (let [grid (Gtk/Grid. #js {:row_spacing 8
                                      :column_spacing 8
                                      :halign Gtk/Align.CENTER})]
             (doseq [[i [k v]] (zipmap (range) user)
                     :let [key-label
                           (Gtk/Label. #js {:label (name k)
                                            :yalign 0.0
                                            :xalign 1.0})
                           _ (.add_css_class key-label "heading")
                           value-label
                           (Gtk/Label. #js {:label (str v)
                                            :xalign 0.0
                                            :selectable true
                                            :wrap-mode Pango/WrapMode.WORD_CHAR
                                            :wrap true})]]
               (.attach grid key-label 0 i 1 1)
               (.attach grid value-label 1 i 1 1))
             grid))))]])

