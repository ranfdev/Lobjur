(ns test.api.server-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [api.server :as s]
            [api.router :as router]
            [api.debug :as debug]
            [api.rest :as r]))

;; Simple test handler
(def test-app
  (r/routes
   (r/route "/" (fn [_] (js/Promise.resolve {:message "root"})))
   (r/route "/items" (fn [_] (js/Promise.resolve {:message "items"})))
   (r/route "/items/{id}" (fn [req] (js/Promise.resolve {:id (get-in req [:params :id])})))))

(defn- delayed-response
  [value delay-ms]
  (js/Promise. (fn [resolve _reject]
                 (js/setTimeout #(resolve value) delay-ms))))

;; Test basic request dispatch
(deftest test-request-get
  (testing "GET request dispatches to handler"
    (async done
      (-> (s/GET test-app "/")
          (.then (fn [res]
                   (is (= "root" (:message res)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " (.-message err)))
                    (done)))))))

(deftest test-request-with-params
  (testing "Request with path params"
    (async done
      (-> (s/GET test-app "in-process://api/items/42")
          (.then (fn [res]
                   (is (= "42" (:id res)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " (.-message err)))
                    (done)))))))

;; Test method-route
(deftest test-method-route
  (testing "method-route matches specific method"
    (let [handler (r/routes
                   (r/method-route :GET "/items" (fn [_] (js/Promise.resolve {:action "list"})))
                   (r/method-route :POST "/items" (fn [_] (js/Promise.resolve {:action "create"}))))]
      (async done
        (-> (s/GET handler "/items")
            (.then (fn [res]
                     (is (= "list" (:action res)))
                     ;; Now test POST
                     (s/POST handler "/items" {:title "New Item"})))
            (.then (fn [res]
                     (is (= "create" (:action res)))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " (.-message err)))
                      (done))))))))

;; Test with-history combinator
(deftest test-with-history
  (testing "with-history records requests and responses"
    (let [history-atom (atom [])
          srv (s/with-history test-app history-atom)]
      (async done
        (-> (s/GET srv "/items")
            (.then (fn [res]
                     (is (= "items" (:message res)))
                     (is (= 1 (count @history-atom)))
                     (let [entry (first @history-atom)]
                       (is (= :ok (:status entry)))
                       (is (= :GET (get-in entry [:request :method])))
                       (is (= "/items" (get-in entry [:request :path])))
                       (is (= {:message "items"} (:response entry)))
                       (is (number? (:duration-ms entry))))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " (.-message err)))
                      (done))))))))

;; Test with-timing combinator
(deftest test-with-timing
  (testing "with-timing adds timing metadata"
    (let [srv (s/with-timing test-app)]
      (async done
        (-> (s/GET srv "/")
            (.then (fn [res]
                     (is (= "root" (:message res)))
                     (is (contains? res :_timing))
                     (is (number? (get-in res [:_timing :duration-ms])))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " (.-message err)))
                      (done))))))))

;; Test with-cache combinator
(deftest test-with-cache
  (testing "with-cache caches in-process GET requests and allows bypass"
    (let [calls (atom 0)
          app (fn [_]
                (swap! calls inc)
                (delayed-response {:message "cached"} 10))
          srv (s/with-cache app)]
      (async done
        (-> (js/Promise.all #js[(s/GET srv "/items")
                                (s/GET srv "/items")])
            (.then (fn [responses]
                     (is (= 1 @calls))
                     (is (= "cached" (:message (aget responses 0))))
                     (s/request srv {:method :GET :path "/items" :cache? false})))
            (.then (fn [_]
                     (is (= 2 @calls))
                     (done)))
            (.catch (fn [err]
                       (is false (str "Unexpected error: " (.-message err)))
                       (done))))))))

(deftest test-with-cache-distinguishes-routes-and-query
  (testing "with-cache keeps separate entries for exact route and query"
    (let [calls (atom 0)
          app (fn [request]
                (swap! calls inc)
                (js/Promise.resolve {:path (:path request)
                                     :query (:query request)}))
          srv (s/with-cache app)]
      (async done
        (-> (s/GET srv "in-process://api/feeds/lobsters/")
            (.then (fn [root-res]
                     (is (= "/feeds/lobsters" (:path root-res)))
                     (s/GET srv "in-process://api/feeds/lobsters/hot/")))
            (.then (fn [hot-res]
                     (is (= "/feeds/lobsters/hot" (:path hot-res)))
                     (s/GET srv "in-process://api/feeds/lobsters/hot/?page=1")))
            (.then (fn [hot-page-res]
                     (is (= "/feeds/lobsters/hot" (:path hot-page-res)))
                     (is (= {:page "1"} (:query hot-page-res)))
                     (is (= 3 @calls))
                     ;; Re-fetch same routes: should now hit cache.
                     (js/Promise.all #js[(s/GET srv "in-process://api/feeds/lobsters/")
                                         (s/GET srv "in-process://api/feeds/lobsters/hot/")
                                         (s/GET srv "in-process://api/feeds/lobsters/hot/?page=1")])))
            (.then (fn [_]
                     (is (= 3 @calls))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " (.-message err)))
                      (done))))))))

(deftest test-with-cache-query-matching-uses-values
  (testing "with-cache should match query params by value, not JS object identity"
    (let [calls (atom 0)
          app (fn [_request]
                (swap! calls inc)
                (js/Promise.resolve {:calls @calls}))
          srv (s/with-cache app)
          query #js {:page "1"}]
      (async done
        (-> (s/request srv {:method :GET :path "/items" :query query})
            (.then (fn [_]
                     (aset query "page" "2")
                     (s/request srv {:method :GET :path "/items" :query query})))
            (.then (fn [_]
                     (is (= 2 @calls))
                     (done)))
            (.catch (fn [err]
                       (is false (str "Unexpected error: " (.-message err)))
                       (done))))))))

(deftest test-router-debugger-path-records-cached-requests
  (testing "router server should record repeated debugger requests even when cached"
    (debug/clear-history!)
    (async done
      (-> (s/request router/server {:method :GET :path "/"})
          (.then (fn [_]
                   (s/request router/server {:method :GET :path "/"})))
          (.then (fn [_]
                   (is (= 2 (count @debug/*history*)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " (.-message err)))
                    (done)))))))

;; Test ->server composition
(deftest test-server-composition
  (testing "->server composes combinators"
    (let [history-atom (atom [])
          srv (s/->server test-app
                          s/with-timing
                          [s/with-history history-atom])]
      (async done
        (-> (s/GET srv "/items")
            (.then (fn [res]
                     (is (= "items" (:message res)))
                     (is (contains? res :_timing))
                     (is (= 1 (count @history-atom)))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " (.-message err)))
                      (done))))))))

;; Test with-error-handler
(deftest test-with-error-handler
  (testing "with-error-handler catches errors and wraps them"
    (let [failing-app (fn [_] (js/Promise.reject (js/Error. "Something broke")))
          srv (s/with-error-handler failing-app)]
      (async done
        (-> (s/GET srv "/anything")
            (.then (fn [res]
                     (is (true? (:_error res)))
                     (is (= "Something broke" (:message res)))
                     (done)))
            (.catch (fn [err]
                      (is false (str "Should not reject: " (.-message err)))
                      (done))))))))
