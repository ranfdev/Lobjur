(ns lobjur.widgets.network-debugger
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gtk" :as Gtk]
   [api.debug :as debug]
   [cljs.pprint :refer [pprint]]
   [clojure.string :as str]))

(defonce debugger-window* (atom nil))

(defn- request-url [{:keys [path query]}]
  (let [path (or path "")]
    (if (seq query)
      (str path "?"
         (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) query)))
      path)))

(defn- in-process-entry? [entry]
  (let [path (or (get-in entry [:request :path]) "")]
    (and (string? path)
         (not (str/starts-with? path "https://"))
         (not (str/starts-with? path "http://")))))

(defn- pretty-str [value]
  (if (string? value)
    value
    (with-out-str
      (binding [*print-level* 6
                *print-length* 200]
        (pprint value)))))

(defn- truncate-text [s max-len]
  (if (> (count s) max-len)
    (str (subs s 0 max-len) "\n… [truncated]")
    s))

(defn- response-summary [response]
  (cond
    (map? response)
    (let [links (keys (get response :_links {}))
          embedded (get response :_embedded {})
          embedded-summary (when (map? embedded)
                             (str/join ", "
                                       (for [[k v] embedded]
                                         (str (name k) "=" (if (coll? v) (count v) 1)))))]
      (str "type: map"
            "\nkeys: " (str/join ", " (map #(if (keyword? %) (name %) (str %)) (keys response)))
            (when (seq links)
             (str "\nlinks: " (str/join ", " (map #(if (keyword? %) (name %) (str %)) links))))
           (when (seq embedded-summary)
             (str "\nembedded: " embedded-summary))))

    (string? response) (str "type: string (" (count response) " chars)")
    (nil? response) "type: nil"
    :else (str "type: " (type response))))

(defn- set-text! [text-view text]
  (let [buffer (.get_buffer ^js text-view)]
    (.set_text ^js buffer (or text "") -1)))

(defn- clear-list! [list-box]
  (loop [child (.get_first_child ^js list-box)]
    (when child
      (let [next-child (.get_next_sibling ^js child)]
        (.remove ^js list-box child)
        (recur next-child)))))

(defn- find-row-by-entry-id [list-box entry-id]
  (loop [row (.get_first_child ^js list-box)]
    (cond
      (nil? row) nil
      (= entry-id (aget row "entryId")) row
      :else (recur (.get_next_sibling ^js row)))))

(defn- make-row [entry]
  (let [row (Gtk/ListBoxRow.)
        box (Gtk/Box. #js {:orientation Gtk/Orientation.VERTICAL :spacing 2
                           :margin_top 6 :margin_bottom 6 :margin_start 8 :margin_end 8})
        title (Gtk/Label. #js {:xalign 0.0 :wrap true})
        subtitle (Gtk/Label. #js {:xalign 0.0})
        method (-> (get-in entry [:request :method] :GET) name str/upper-case)
        status (if (= :ok (:status entry)) "ok" "error")
        time (or (:timestamp entry) "")
        subtitle-text (str status " · " (:duration-ms entry) "ms · " time)]
    (.set_label ^js title (str method " " (request-url (:request entry))))
    (.set_label ^js subtitle subtitle-text)
    (.add_css_class ^js subtitle "dim-label")
    (.append ^js box title)
    (.append ^js box subtitle)
    (.set_child ^js row box)
    (aset row "entryId" (:id entry))
    row))

(defn- entry-details-text [entry]
  (let [request (:request entry)
        method (-> (get request :method :GET) name str/upper-case)
        url (request-url request)
        status (if (= :ok (:status entry))
                 "ok"
                 (str "error - " (:error entry)))
        request-body (some-> (:body request) pretty-str (truncate-text 6000))
        response (:response entry)
        response-body (if (= :ok (:status entry))
                        (truncate-text (pretty-str response) 12000)
                        (or (:error entry) "Unknown error"))]
    (str "Method: " method
         "\nURL: " url
         "\nStatus: " status
         "\nDuration: " (:duration-ms entry) "ms"
         "\nTimestamp: " (:timestamp entry)
         (when (seq (:query request))
           (str "\nQuery: " (pretty-str (:query request))))
         (when request-body
           (str "\nRequest Body:\n" request-body))
         "\n\nResponse Summary:\n"
         (if (= :ok (:status entry))
           (response-summary response)
           "type: error")
         "\n\nResponse Body:\n"
         response-body)))

(defn- visible-entries [query-text]
  (let [q (str/lower-case (or query-text ""))]
    (->> @debug/*history*
         (filter in-process-entry?)
         (filter (fn [entry]
                   (if (str/blank? q)
                     true
                     (let [target (str/lower-case
                                   (str (get-in entry [:request :method] "")
                                        " "
                                        (request-url (:request entry))))]
                       (str/includes? target q)))))
         reverse
         vec)))

(defn- build-window [app parent on-close]
  (let [win (Adw/ApplicationWindow.
             #js {:application app
                  :title "Network Debugger"
                  :default_width 980
                  :default_height 640})
        _ (when parent (.set_transient_for ^js win parent))
        toolbar (Adw/ToolbarView.)
        header (Adw/HeaderBar.)
        list-box (Gtk/ListBox.)
        list-scroll (Gtk/ScrolledWindow.)
        details-view (Gtk/TextView.)
        details-scroll (Gtk/ScrolledWindow.)
        search-entry (Gtk/SearchEntry.)
        pause-btn (Gtk/ToggleButton. #js {:label "Pause"})
        clear-btn (Gtk/Button. #js {:label "Clear"})
        paned (Gtk/Paned. #js {:orientation Gtk/Orientation.HORIZONTAL})
        ui-state (atom {:query "" :selected-id nil :paused? false})
        watch-key (str "network-debugger-" (js/Date.now) "-" (rand-int 1000000))]
    (.set_title_widget ^js header (Adw/WindowTitle. #js {:title "In-Process REST Debugger"}))
    (.set_placeholder_text ^js search-entry "Filter by method or URL")
    (.set_size_request ^js search-entry 360 -1)
    (.pack_start ^js header search-entry)
    (.pack_end ^js header pause-btn)
    (.pack_end ^js header clear-btn)
    (.add_top_bar ^js toolbar header)

    (.set_selection_mode ^js list-box Gtk/SelectionMode.SINGLE)
    (.set_activate_on_single_click ^js list-box true)
    (.set_policy ^js list-scroll Gtk/PolicyType.NEVER Gtk/PolicyType.AUTOMATIC)
    (.set_child ^js list-scroll list-box)

    (.set_editable ^js details-view false)
    (.set_monospace ^js details-view true)
    (.set_wrap_mode ^js details-view Gtk/WrapMode.WORD_CHAR)
    (.set_policy ^js details-scroll Gtk/PolicyType.AUTOMATIC Gtk/PolicyType.AUTOMATIC)
    (.set_child ^js details-scroll details-view)

    (.set_start_child ^js paned list-scroll)
    (.set_end_child ^js paned details-scroll)
    (.set_resize_start_child ^js paned true)
    (.set_shrink_start_child ^js paned false)
    (.set_position ^js paned 420)

    (.set_content ^js toolbar paned)
    (.set_content ^js win toolbar)

    (letfn [(render! []
              (let [entries (visible-entries (:query @ui-state))
                    selected-id (or (:selected-id @ui-state)
                                    (some-> entries first :id))
                    selected-entry (first (filter #(= selected-id (:id %)) entries))]
                (clear-list! list-box)
                (if (seq entries)
                  (doseq [entry entries]
                    (.append ^js list-box (make-row entry)))
                  (set-text! details-view "No in-process requests captured yet."))
                (if-let [row (and selected-entry (find-row-by-entry-id list-box selected-id))]
                  (do
                    (.select_row ^js list-box row)
                    (set-text! details-view (entry-details-text selected-entry)))
                  (do
                    (.select_row ^js list-box nil)
                    (when (seq entries)
                      (set-text! details-view "Select a request to inspect response details."))))
                (swap! ui-state assoc :selected-id (some-> selected-entry :id))))]
      (.connect list-box "row-selected"
                (fn [_ row]
                  (if row
                    (let [entry-id (aget row "entryId")
                          entry (first (filter #(= entry-id (:id %))
                                               (visible-entries (:query @ui-state))))]
                      (swap! ui-state assoc :selected-id entry-id)
                      (set-text! details-view
                                 (if entry
                                   (entry-details-text entry)
                                   "Select a request to inspect response details.")))
                    (swap! ui-state assoc :selected-id nil))))
      (.connect search-entry "search-changed"
                (fn [entry]
                  (swap! ui-state assoc :query (.get_text ^js entry))
                  (render!)))
      (.connect clear-btn "clicked"
                (fn [_]
                  (swap! ui-state assoc :selected-id nil)
                  (debug/clear-history!)
                  (render!)))
      (.connect pause-btn "toggled"
                (fn [btn]
                  (let [paused? (.get_active ^js btn)]
                    (swap! ui-state assoc :paused? paused?)
                    (when-not paused?
                      (render!)))))
      (add-watch debug/*history* watch-key
                 (fn [_ _ _ _]
                   (when-not (:paused? @ui-state)
                     (render!))))
      (.connect win "close-request"
                (fn [_]
                  (remove-watch debug/*history* watch-key)
                  (when on-close (on-close))
                  false))
      (render!))
    win))

(defn open! [app parent]
  (if-let [win @debugger-window*]
    (.present ^js win)
    (let [win (build-window app parent #(reset! debugger-window* nil))]
      (reset! debugger-window* win)
      (.present ^js win))))
