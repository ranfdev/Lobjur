(ns test.api.hal-test
  (:require [cljs.test :refer [deftest is testing]]
            [api.hal :as hal]))

(deftest test-paginated-without-query-params
  (testing "Pagination without query parameters"
    (let [resource {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/feeds/lobsters/hot" 2
                                :has-next true
                                :has-prev true)]
      
      (testing "should add next link with page parameter"
        (is (= "in-process://api/feeds/lobsters/hot?page=3"
               (get-in result [:_links :next :href]))))
      
      (testing "should add prev link with page parameter"
        (is (= "in-process://api/feeds/lobsters/hot?page=1"
               (get-in result [:_links :prev :href])))))))

(deftest test-paginated-with-query-params
  (testing "Pagination with existing query parameters"
    (let [resource {:_links {:self {:href "in-process://api/users/jcs/stories?source=hn"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/users/jcs/stories" 1
                                :has-next true
                                :has-prev false
                                :query-params {:source "hn"})]
      
      (testing "should preserve source parameter in next link"
        (is (= "in-process://api/users/jcs/stories?source=hn&page=2"
               (get-in result [:_links :next :href]))))
      
      (testing "should not add prev link when has-prev is false"
        (is (nil? (get-in result [:_links :prev])))))))

(deftest test-paginated-with-multiple-query-params
  (testing "Pagination with multiple query parameters"
    (let [resource {:_links {:self {:href "in-process://api/tags/programming/stories"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/tags/programming/stories" 3
                                :has-next true
                                :has-prev true
                                :query-params {:source "lobsters" :format "json"})]
      
      (testing "should preserve all query parameters in next link"
        (let [next-href (get-in result [:_links :next :href])]
          (is (re-find #"source=lobsters" next-href))
          (is (re-find #"format=json" next-href))
          (is (re-find #"page=4" next-href))))
      
      (testing "should preserve all query parameters in prev link"
        (let [prev-href (get-in result [:_links :prev :href])]
          (is (re-find #"source=lobsters" prev-href))
          (is (re-find #"format=json" prev-href))
          (is (re-find #"page=2" prev-href)))))))

(deftest test-paginated-first-page
  (testing "Pagination on first page"
    (let [resource {:_links {:self {:href "in-process://api/feeds/hn/top"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/feeds/hn/top" 1
                                :has-next true
                                :has-prev false)]
      
      (testing "should not add prev link"
        (is (nil? (get-in result [:_links :prev]))))
      
      (testing "should add next link"
        (is (= "in-process://api/feeds/hn/top?page=2"
               (get-in result [:_links :next :href])))))))

(deftest test-paginated-last-page
  (testing "Pagination on last page"
    (let [resource {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/feeds/lobsters/hot" 5
                                :has-next false
                                :has-prev true)]
      
      (testing "should add prev link"
        (is (= "in-process://api/feeds/lobsters/hot?page=4"
               (get-in result [:_links :prev :href]))))
      
      (testing "should not add next link"
        (is (nil? (get-in result [:_links :next])))))))

(deftest test-paginated-preserves-original-resource
  (testing "Pagination should preserve original resource data"
    (let [resource {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}
                             :tags {:href "in-process://api/tags"}}
                    :_embedded {:stories [{:title "Story 1"}]}
                    :total 42}
          result (hal/paginated resource "/feeds/lobsters/hot" 2
                                :has-next true
                                :has-prev true
                                :query-params {:source "lobsters"})]
      
      (testing "should preserve original links"
        (is (= {:href "in-process://api/tags"}
               (get-in result [:_links :tags]))))
      
      (testing "should preserve embedded data"
        (is (= [{:title "Story 1"}]
               (get-in result [:_embedded :stories]))))
      
      (testing "should preserve extra fields"
        (is (= 42 (:total result)))))))

(deftest test-paginated-edge-cases
  (testing "Pagination with empty query params"
    (let [resource {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/feeds/lobsters/hot" 1
                                :has-next true
                                :query-params {})]
      
      (testing "should handle empty query params map"
        (is (= "in-process://api/feeds/lobsters/hot?page=2"
               (get-in result [:_links :next :href]))))))
  
  (testing "Pagination with nil query params"
    (let [resource {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}}
                    :_embedded {:stories []}}
          result (hal/paginated resource "/feeds/lobsters/hot" 1
                                :has-next true
                                :query-params nil)]
      
      (testing "should handle nil query params"
        (is (= "in-process://api/feeds/lobsters/hot?page=2"
               (get-in result [:_links :next :href])))))))
