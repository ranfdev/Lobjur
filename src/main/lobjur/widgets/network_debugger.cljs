(ns lobjur.widgets.network-debugger
  (:require
   ["gjs.gi.Adw" :as Adw]
   ["gjs.gi.Gdk" :as Gdk]
   ["gjs.gi.Gtk" :as Gtk]
   [api.debug :as debug]
   [api.router :as router]
   [api.server :as server]
   [api.url :as url]
   [cljs.pprint :refer [pprint]]
   [cljs.reader :as reader]
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

(defn- pretty-str
  ([value]
   (pretty-str value {:print-level 12 :print-length 2000}))
  ([value {:keys [print-level print-length]}]
   (if (string? value)
     value
     (with-out-str
       (binding [*print-level* print-level
                 *print-length* print-length]
         (pprint value))))))

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

(defn- get-buffer-text [buffer]
  (let [start (.get_start_iter ^js buffer)
        end (.get_end_iter ^js buffer)]
    (.get_text ^js buffer start end false)))

(defn- set-buffer-text! [buffer text]
  (.set_text ^js buffer (or text "") -1))

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
        response (:response entry)]
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
           "type: error"))))

(defn- entry-response-text [entry]
  (if (nil? entry)
    ""
    (let [response-body (if (= :ok (:status entry))
                          (pretty-str (:response entry) {:print-level 12
                                                         :print-length 2500})
                          (or (:error entry) "Unknown error"))]
      (truncate-text response-body 40000))))

(defn- make-response-pane []
  (let [view (Gtk/TextView.)]
      (.set_editable ^js view false)
      (.set_monospace ^js view true)
      (.set_vexpand ^js view true)
      {:widget view
       :set-response! (fn [entry]
                        (set-text! view (entry-response-text entry)))}))

(defn- method->text [method]
  (-> (or method :GET) name str/upper-case))

(defn- body->text [body]
  (cond
    (nil? body) ""
    (string? body) body
    :else (pretty-str body)))

(defn- entry->form [entry]
  (let [request (:request entry)]
    {:method (method->text (:method request))
     :url (request-url request)
     :body (body->text (:body request))}))

(defn- parse-body-text [text]
  (let [trimmed (str/trim (or text ""))]
    (if (str/blank? trimmed)
      nil
      (try
        (js->clj (js/JSON.parse trimmed) :keywordize-keys true)
        (catch :default _
          (try
            (reader/read-string trimmed)
            (catch :default _
              trimmed)))))))

(defn- form->request [method-text url-text body-text bypass-cache?]
  (let [method (let [m (-> (or method-text "") str/trim str/upper-case)]
                 (if (str/blank? m) "GET" m))
        {:keys [path query-params]} (url/parse-url (str/trim (or url-text "")))]
    (cond-> {:method (keyword method)
             :path path
             :query query-params
             :body (parse-body-text body-text)}
      bypass-cache? (assoc :cache? false))))

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
                  :default_height 700})
        _ (when parent (.set_transient_for ^js win parent))
        toolbar (Adw/ToolbarView.)
        header (Adw/HeaderBar.)
        list-box (Gtk/ListBox.)
        list-scroll (Gtk/ScrolledWindow.)
        search-entry (Gtk/SearchEntry.)
        pause-btn (Gtk/ToggleButton. #js {:label "Pause"})
        clear-btn (Gtk/Button. #js {:label "Clear"})
        paned (Gtk/Paned. #js {:orientation Gtk/Orientation.HORIZONTAL})
        right-paned (Gtk/Paned. #js {:orientation Gtk/Orientation.VERTICAL})
        composer-box (Gtk/Box. #js {:orientation Gtk/Orientation.VERTICAL
                                    :spacing 8
                                    :margin_top 8
                                    :margin_bottom 8
                                    :margin_start 8
                                    :margin_end 8})
        request-row (Gtk/Box. #js {:orientation Gtk/Orientation.HORIZONTAL :spacing 8})
        action-row (Gtk/Box. #js {:orientation Gtk/Orientation.HORIZONTAL :spacing 8})
        method-entry (Gtk/Entry. #js {:width_chars 7 :hexpand false})
        url-entry (Gtk/Entry. #js {:hexpand true})
        send-btn (Gtk/Button. #js {:label "Send"})
        replay-btn (Gtk/Button. #js {:label "Replay Selected"})
        clear-form-btn (Gtk/Button. #js {:label "Clear Form"})
        bypass-label (Gtk/Label. #js {:label "Bypass cache" :xalign 0.0})
        bypass-switch (Gtk/Switch. #js {:active true :valign Gtk/Align.CENTER})
        body-view (Gtk/TextView.)
        body-buffer (.get_buffer ^js body-view)
        body-scroll (Gtk/ScrolledWindow.)
        details-view (Gtk/TextView.)
        details-scroll (Gtk/ScrolledWindow.)
        response-pane (make-response-pane)
        response-scroll (Gtk/ScrolledWindow.)
        response-box (Gtk/Box. #js {:orientation Gtk/Orientation.VERTICAL
                                    :spacing 8
                                    :margin_top 8
                                    :margin_bottom 8
                                    :margin_start 8
                                    :margin_end 8})
        ui-state (atom {:query ""
                        :selected-id nil
                        :paused? false
                        :hydrated-id nil
                        :sending? false})
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

    (.set_placeholder_text ^js method-entry "GET")
    (.set_text ^js method-entry "GET")
    (.set_placeholder_text ^js url-entry "/feeds/lobsters/hot")
    (.set_hexpand ^js url-entry true)
    (.set_wrap_mode ^js body-view Gtk/WrapMode.WORD_CHAR)
    (.set_monospace ^js body-view true)
    (.set_policy ^js body-scroll Gtk/PolicyType.AUTOMATIC Gtk/PolicyType.AUTOMATIC)
    (.set_min_content_height ^js body-scroll 120)
    (.set_child ^js body-scroll body-view)

    (.append ^js request-row method-entry)
    (.append ^js request-row url-entry)
    (.append ^js request-row send-btn)
    (.append ^js action-row replay-btn)
    (.append ^js action-row clear-form-btn)
    (.append ^js action-row bypass-label)
    (.append ^js action-row bypass-switch)
    (.append ^js composer-box request-row)
    (.append ^js composer-box action-row)
    (.append ^js composer-box body-scroll)

    (.set_editable ^js details-view false)
    (.set_monospace ^js details-view true)
    (.set_wrap_mode ^js details-view Gtk/WrapMode.WORD_CHAR)
    (.set_policy ^js details-scroll Gtk/PolicyType.AUTOMATIC Gtk/PolicyType.AUTOMATIC)
    (.set_min_content_height ^js details-scroll 180)
    (.set_child ^js details-scroll details-view)

    (.set_policy ^js response-scroll Gtk/PolicyType.AUTOMATIC Gtk/PolicyType.AUTOMATIC)
    (.set_child ^js response-scroll (:widget response-pane))

    (.append ^js response-box details-scroll)
    (.append ^js response-box response-scroll)

    (.set_start_child ^js right-paned composer-box)
    (.set_end_child ^js right-paned response-box)
    (.set_resize_start_child ^js right-paned false)
    (.set_shrink_start_child ^js right-paned false)
    (.set_position ^js right-paned 220)

    (.set_start_child ^js paned list-scroll)
    (.set_end_child ^js paned right-paned)
    (.set_resize_start_child ^js paned true)
    (.set_shrink_start_child ^js paned false)
    (.set_position ^js paned 420)

    (.set_content ^js toolbar paned)
    (.set_content ^js win toolbar)

    (letfn [(selected-entry []
              (let [entry-id (:selected-id @ui-state)]
                (first (filter #(= entry-id (:id %))
                               (visible-entries (:query @ui-state))))))
            (update-replay-sensitive! []
              (.set_sensitive ^js replay-btn
                              (and (not (:sending? @ui-state))
                                   (some? (selected-entry)))))
            (hydrate-composer! [entry]
              (let [{:keys [method url body]} (entry->form entry)]
                (.set_text ^js method-entry method)
                (.set_text ^js url-entry url)
                (set-buffer-text! body-buffer body)))
            (show-entry! [entry]
              (if entry
                (do
                  (set-text! details-view (entry-details-text entry))
                  ((:set-response! response-pane) entry))
                (do
                  (set-text! details-view "Select a request to inspect response details.")
                  ((:set-response! response-pane) nil))))
            (set-sending! [sending?]
              (swap! ui-state assoc :sending? sending?)
              (.set_sensitive ^js send-btn (not sending?))
              (.set_sensitive ^js clear-form-btn (not sending?))
              (update-replay-sensitive!))
            (send-current! []
              (try
                (let [request (form->request (.get_text ^js method-entry)
                                             (.get_text ^js url-entry)
                                             (get-buffer-text body-buffer)
                                             (.get_active ^js bypass-switch))]
                  (set-sending! true)
                   (-> (server/request router/server request)
                       (.then (fn [_]
                                (let [latest-id (:id (peek @debug/*history*))]
                                  (set-sending! false)
                                  (swap! ui-state assoc :selected-id latest-id :hydrated-id latest-id)
                                  (render!))))
                       (.catch (fn [_]
                                 (let [latest-id (:id (peek @debug/*history*))]
                                   (set-sending! false)
                                   (swap! ui-state assoc :selected-id latest-id :hydrated-id latest-id)
                                   (render!))))))
                 (catch :default e
                   (set-sending! false)
                   (set-text! details-view (str "Failed to send request: " (.-message e)))
                   ((:set-response! response-pane) nil))))
            (render! []
              (let [entries (visible-entries (:query @ui-state))
                    selected-id (or (:selected-id @ui-state)
                                    (some-> entries first :id))
                    selected-entry (first (filter #(= selected-id (:id %)) entries))]
                (clear-list! list-box)
                (if (seq entries)
                  (doseq [entry entries]
                    (.append ^js list-box (make-row entry)))
                  (do
                    (set-text! details-view "No in-process requests captured yet.")
                    ((:set-response! response-pane) nil)))
                (if-let [row (and selected-entry (find-row-by-entry-id list-box selected-id))]
                  (do
                    (.select_row ^js list-box row)
                    (show-entry! selected-entry))
                  (do
                    (.select_row ^js list-box nil)
                    (when (seq entries)
                      (show-entry! nil))))
                (swap! ui-state assoc :selected-id (some-> selected-entry :id))
                (update-replay-sensitive!)))]
      (.connect list-box "row-selected"
                (fn [_ row]
                  (if row
                    (let [entry-id (aget row "entryId")
                          entry (first (filter #(= entry-id (:id %))
                                               (visible-entries (:query @ui-state))))]
                      (swap! ui-state assoc :selected-id entry-id)
                      (show-entry! entry)
                      (when (and entry
                                 (not= entry-id (:hydrated-id @ui-state)))
                        (hydrate-composer! entry)
                        (swap! ui-state assoc :hydrated-id entry-id))
                      (update-replay-sensitive!))
                    (do
                      (swap! ui-state assoc :selected-id nil)
                      (update-replay-sensitive!)))))
      (.connect search-entry "search-changed"
                (fn [entry]
                  (swap! ui-state assoc :query (.get_text ^js entry))
                  (render!)))
      (.connect clear-btn "clicked"
                (fn [_]
                  (swap! ui-state assoc :selected-id nil :hydrated-id nil)
                  (debug/clear-history!)
                  (render!)))
      (.connect pause-btn "toggled"
                (fn [btn]
                  (let [paused? (.get_active ^js btn)]
                    (swap! ui-state assoc :paused? paused?)
                    (when-not paused?
                      (render!)))))
      (.connect send-btn "clicked" (fn [_] (send-current!)))
      (.connect replay-btn "clicked"
                (fn [_]
                  (when-let [entry (selected-entry)]
                    (hydrate-composer! entry)
                    (swap! ui-state assoc :hydrated-id (:id entry))
                    (send-current!))))
      (.connect clear-form-btn "clicked"
                (fn [_]
                  (.set_text ^js method-entry "GET")
                  (.set_text ^js url-entry "")
                  (set-buffer-text! body-buffer "")))
      (let [controller (Gtk/EventControllerKey.)]
        (.connect controller "key-pressed"
                  (fn [_ keyval _ state]
                    (let [ctrl? (not= 0 (bit-and state Gdk/ModifierType.CONTROL_MASK))
                          enter? (or (= keyval Gdk/KEY_Return)
                                     (= keyval Gdk/KEY_KP_Enter))]
                      (if (and ctrl? enter?)
                        (do
                          (send-current!)
                          true)
                        false))))
        (.add_controller ^js body-view controller))
       (add-watch debug/*history* watch-key
                  (fn [_ _ _ _]
                    (when (and (not (:paused? @ui-state))
                               (not (:sending? @ui-state)))
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
