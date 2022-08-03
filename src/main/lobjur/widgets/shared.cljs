(ns lobjur.widgets.shared
  (:require
   ["gjs.gi.Gtk" :as Gtk]
   [clojure.core.async :as async]
   [rollui.core :as rollui]))

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

(def swr-cache (atom {}))
(defn -swr-mutate [k f refresh-interval]
  (when-let [timer (get-in @swr-cache [k :timer])]
    (js/clearTimeout timer))

  (swap! swr-cache assoc k {:promise (f)
                            :timer
                            (when-let [t refresh-interval]
                              (js/setTimeout f t))}))

(defn swr
  "Inspired by https://swr.vercel.app/.
  May break when two components use the same key."
  [k f & opts]
  (let [responses (async/chan 1)
        mutate! #(-swr-mutate
                  k f
                  (:refresh-interval opts))]
    (add-watch swr-cache (keyword (str k "__swr"))
               (fn [_ _ _ latest-cache]
                 (when-let [prom ^js (get-in latest-cache [k :promise])]
                   (-> prom
                       (.then
                        (fn [data]
                          (set! (.-ready prom) true)
                          (async/put! responses {:data data :error nil})))
                       (.catch
                        (fn [err]
                          (async/put! responses {:data nil :error err})))))))

    (if-let [prom (get-in @swr-cache [k :promise])]
      (if (not (.-ready prom))
        (async/put! responses {:data nil :error nil})
        (-> prom
            (.then #(async/put! responses {:data % :error nil}))
            (.catch #(async/put! responses {:data nil :error %}))))
      (do
        (async/put! responses {:data nil :error nil})
        (swap! swr-cache assoc k {:promise (f)})))
    {:responses responses
     :mutate mutate!}))
