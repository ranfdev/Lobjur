(ns rollui.core
  (:require
   [clojure.core.async :as async]
   [clojure.core.async.impl.protocols :refer [Channel]]))

(declare build-widget)
(defn build-property [p]
  (if (vector? p)
    (build-widget p)
    p))

(defn update-prop-once [w k vs]
  (when (not= vs ::abort)
    (case (first (name k))
      "." (js-invoke w (subs (name k) 1) (build-property vs))
      "$" (.connect w (subs (name k) 1) vs)
      (aset w (name k) (build-property vs)))))

(defn update-prop-many [w k vs]
  (cond
    (= ::ref k)
    (reset! vs w)
    (= ::ref-in k)
    (swap! (first vs) assoc (nth vs 1) w)
    (instance? js/Promise vs)
    (-> vs
        (.then (partial update-prop-many w k))
        (.catch (partial println "Error in promise, in update-prop-many")))
    (satisfies? Channel vs)
    (async/go-loop []
      (when-some [v (async/<! vs)]
        (update-prop-many w k v)
        (recur)))
    (and (satisfies? ISeq vs) (not (vector? vs)))
    (doseq [v vs]
      (update-prop-many w k v))
    (satisfies? IWatchable vs)
    (do
      (update-prop-many w k @vs)
      (add-watch vs nil (fn [_ _ _old current] (update-prop-many w k current))))
    :else (update-prop-once w k vs)))

(defn build-widget [[widget & args]]
  (when (not (fn? widget))
    (println "Can't build widget with constructor" widget ".\n"
             "The constructor arguments were" args))
  (let [w (new widget)]
    (doseq [[k vs] (partition 2 args)]
      (update-prop-many w k vs))
    w))

(defn build-ui [widget]
  (build-widget widget))

(defn derived-atom
  ;; From https://github.com/tonsky/rum/blob/4ff9ddc98d65f1a287bab2f02dcaa0a718cd16b8/src/rum/derived_atom.cljc
  ;; LICENSE: Eclipse Public License - v 1.0 https://github.com/tonsky/rum/blob/4ff9ddc98d65f1a287bab2f02dcaa0a718cd16b8/LICENSE
  ([refs key f]
   (derived-atom refs key f {}))
  ([refs key f opts]
   (let [{:keys [ref check-equals?]
          :or {check-equals? true}} opts
         recalc (case (count refs)
                  1 (let [[a] refs] #(f @a))
                  2 (let [[a b] refs] #(f @a @b))
                  3 (let [[a b c] refs] #(f @a @b @c))
                  #(apply f (map deref refs)))
         sink   (if ref
                  (doto ref (reset! (recalc)))
                  (atom (recalc)))
         watch  (if check-equals?
                  (fn [_ _ _ _]
                    (let [new-val (recalc)]
                      (when (not= @sink new-val)
                        (reset! sink new-val))))
                  (fn [_ _ _ _]
                    (reset! sink (recalc))))]
     (doseq [ref refs]
       (add-watch ref key watch))
     sink)))

(def ^js DataObject
  (js*
   "
(() => {
  const {GObject} = imports.gi;
  return GObject.registerClass({
  }, class DataObject extends GObject.Object {
    constructor(d) {
      super();
      this.data = d;
    }
 });
})()
   "))
(def ^js RefsWidgetRaw
  (js*
   "
(() => {
  const {GObject, Adw} = imports.gi;
  return GObject.registerClass({
  }, class RefsWidget extends Adw.Bin {
      constructor(refs) {
        super();
        this.refs = refs;
      } 
    });
})()
    "))

(defn ^js RefsWidget
  "Takes a rollui widget (view), returns a GtkWidget with the specified view rendered into it.
  The rollui widget is able to save references to the internal sub-widgets. The returned
  GtkWidget will store those references so that they can be used later.
  This is useful especially when building a GtkListView."
  [view]
  (let [refs (atom {})
        refs-widget (RefsWidgetRaw. refs)]
    (doto refs-widget
      (.set_child (build-ui (view refs))))))
