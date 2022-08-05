(ns lobjur.widgets.window
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]
   [lobjur.state :as state :refer [global-widgets]]
   [rollui.core :as rollui :refer [derived-atom] :refer-macros [defc]]))

(declare header-bar)
(defc header-bar []

  [Adw/HeaderBar
   :.pack_start [Adw/Bin :child
                 (derived-atom [state/state]
                               :header-start #(get % :header-start nil))]
   :.pack_end [Adw/Bin :child
                 (derived-atom [state/state]
                               :header-end #(get % :header-end nil))]
   :title_widget (derived-atom
                  [state/state] :title-widget #(get % :title-widget nil))])

(defn window-content []
  [Gtk/Box
   :orientation Gtk/Orientation.VERTICAL
   :.append
   (header-bar)
   :.append
   [Gtk/Stack
    ::rollui/ref-in [global-widgets :main-stack]
    :transition-type Gtk/StackTransitionType.OVER_LEFT_RIGHT]])

