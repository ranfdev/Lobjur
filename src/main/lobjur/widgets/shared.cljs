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

;;taken from https://stackoverflow.com/a/69122877/11189772
(def time-ago (js* "function(input) {
  const date = (input instanceof Date) ? input : new Date(input);
  const formatter = new Intl.RelativeTimeFormat('en');
  const ranges = {
    years: 3600 * 24 * 365,
    months: 3600 * 24 * 30,
    weeks: 3600 * 24 * 7,
    days: 3600 * 24,
    hours: 3600,
    minutes: 60,
    seconds: 1
  };
  const secondsElapsed = (date.getTime() - Date.now()) / 1000;
  for (let key in ranges) {
    if (ranges[key] < Math.abs(secondsElapsed)) {
      const delta = secondsElapsed / ranges[key];
      return formatter.format(Math.round(delta), key);
    }
  }
}
"))
