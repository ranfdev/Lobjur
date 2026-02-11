(ns lobjur.widgets.shared
  (:require
   ["gjs.gi.Gtk" :as Gtk]))

(defn upvote-btn [score]
  [Gtk/Box
   :margin-start 8
   :margin-end 8
   :orientation Gtk/Orientation.VERTICAL
   :valign Gtk/Align.CENTER
   :width_request 16
   :height_request 16
   :.append [Gtk/Image :icon_name "pan-up-symbolic"]
   :.append [Gtk/Label :label (str score) :css_classes #js ["heading" "numeric"]]])

(defn pagination-controls
  [{:keys [prev-sensitive next-sensitive on-prev on-next]}]
  [Gtk/Box
   :hexpand true
   :homogeneous true
   :.append
   [Gtk/Button
    :halign Gtk/Align.START
    :icon-name "pan-start-symbolic"
    :tooltip-text "Previous"
    :css_classes #js ["flat" "circular"]
    :sensitive prev-sensitive
    :$clicked (fn [_] (when on-prev (on-prev)))]
   :.append
   [Gtk/Label :label "•"]
   :.append
   [Gtk/Button
    :halign Gtk/Align.END
    :icon-name "pan-end-symbolic"
    :tooltip-text "Next"
    :css_classes #js ["flat" "circular"]
    :sensitive next-sensitive
    :$clicked (fn [_] (when on-next (on-next)))]])

;;taken from https://stackoverflow.com/a/69122877/11189772
(def time-ago (js* "function(input) {
  const date = (input instanceof Date) ? input : new Date(input);
  const ranges = {
    days: 3600 * 24,
    hours: 3600,
    minutes: 60,
    seconds: 1
  };
  const units = {
    days: 'd',
    hours: 'h',
    minutes: 'm',
    seconds: 's'
  };
  const secondsElapsed = (date.getTime() - Date.now()) / 1000;
  for (let key in ranges) {
    if ((key === 'days')
        ? ranges[key] <= Math.abs(secondsElapsed)
        : ranges[key] < Math.abs(secondsElapsed)) {
      const delta = secondsElapsed / ranges[key];
      const rounded = Math.round(delta);
      const label = Math.abs(rounded) + units[key];
      return rounded < 0 ? label + ' ago' : 'in ' + label;
    }
  }
}
"))
