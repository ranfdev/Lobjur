(ns test.api.helpers-test
  "Comprehensive tests for api.helpers - HAL navigation and extraction helpers"
  (:require [cljs.test :refer-macros [deftest is testing async use-fixtures]]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            [api.helpers :as helpers]
            [api.router :as router]))

;; ============================================================================
;; Test Data Fixtures
;; ============================================================================

(def sample-story-with-embedded
  "Story with embedded author and comments"
  {:id 123
   :title "Test Story"
   :score 42
   :_links {:self {:href "in-process://api/stories/123"}
            :external {:href "https://example.com/article"}
            :comments {:href "in-process://api/stories/123/comments"}}
   :_embedded {:author {:username "jcs"
                        :karma 2937
                        :_links {:self {:href "in-process://api/users/jcs"}}}
               :comments [{:id 1 :text "Great article!"}
                         {:id 2 :text "Thanks for sharing"}]}})

(def sample-story-links-only
  "Story with only links, no embedded resources"
  {:id 456
   :title "Another Story"
   :score 15
   :_links {:self {:href "in-process://api/stories/456"}
            :external {:href "https://example.com/post"}
            :author {:href "in-process://api/users/bob"}
            :comments {:href "in-process://api/stories/456/comments"}}})

(def sample-feed-response
  "Paginated feed response with embedded stories"
  {:_links {:self {:href "in-process://api/feeds/lobsters/hot"}
            :next {:href "in-process://api/feeds/lobsters/hot?page=2"}}
   :_embedded {:stories [{:id 1 :title "Story 1" :score 10}
                        {:id 2 :title "Story 2" :score 20}
                        {:id 3 :title "Story 3" :score 30}]}})

(def sample-feed-last-page
  "Last page of feed with no next link"
  {:_links {:self {:href "in-process://api/feeds/lobsters/hot?page=5"}
            :prev {:href "in-process://api/feeds/lobsters/hot?page=4"}}
   :_embedded {:stories [{:id 99 :title "Last Story" :score 5}]}})

(def sample-author-response
  "Author resource that would be fetched"
  {:username "bob"
   :karma 500
   :about "Software developer"
   :_links {:self {:href "in-process://api/users/bob"}}})

(def sample-comments-response
  "Comments collection that would be fetched"
  {:_links {:self {:href "in-process://api/stories/456/comments"}}
   :_embedded {:comments [{:id 10 :text "Comment 1"}
                         {:id 11 :text "Comment 2"}
                         {:id 12 :text "Comment 3"}]}})

;; ============================================================================
;; Mock Router for Testing
;; ============================================================================

(def mock-responses
  "Map of URLs to mock responses"
  (atom {"in-process://api/users/bob" sample-author-response
         "in-process://api/stories/456/comments" sample-comments-response
         "in-process://api/stories/123/comments" sample-comments-response
         "in-process://api/feeds/lobsters/hot" sample-feed-response
         "in-process://api/feeds/lobsters/hot?page=2" 
         {:_links {:self {:href "in-process://api/feeds/lobsters/hot?page=2"}
                   :prev {:href "in-process://api/feeds/lobsters/hot"}
                   :next {:href "in-process://api/feeds/lobsters/hot?page=3"}}
          :_embedded {:stories [{:id 4 :title "Story 4" :score 40}]}}}))

;; Save original router/server
(def original-server router/server)

;; Mock router/server for testing
(set! router/server
  (fn [request]
    (let [path (:path request)
          query (:query request)
          lookup-url (if (seq query)
                       (str "in-process://api" path "?"
                            (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) query)))
                       (str "in-process://api" path))]
      (if-let [response (get @mock-responses lookup-url)]
        (js/Promise.resolve response)
        (js/Promise.reject (js/Error. (str "Mock not found for URL: " lookup-url)))))))

;; Restore original after tests
(use-fixtures :once
  {:before (fn [])
   :after (fn [] (set! router/server original-server))})

;; ============================================================================
;; Tests: Basic HAL Extraction Helpers
;; ============================================================================

(deftest test-hal-collection
  (testing "Extract embedded collection"
    (is (= [{:id 1 :title "Story 1" :score 10}
            {:id 2 :title "Story 2" :score 20}
            {:id 3 :title "Story 3" :score 30}]
           (helpers/hal-collection sample-feed-response :stories)))
    
    (is (= [{:id 1 :text "Great article!"}
            {:id 2 :text "Thanks for sharing"}]
           (helpers/hal-collection sample-story-with-embedded :comments))))
  
  (testing "Return empty array when collection not found"
    (is (= [] (helpers/hal-collection sample-story-with-embedded :nonexistent)))
    (is (= [] (helpers/hal-collection {} :stories)))))

(deftest test-hal-link
  (testing "Extract link href"
    (is (= "https://example.com/article"
           (helpers/hal-link sample-story-with-embedded :external)))
    
    (is (= "in-process://api/stories/123/comments"
           (helpers/hal-link sample-story-with-embedded :comments)))
    
    (is (= "in-process://api/feeds/lobsters/hot?page=2"
           (helpers/hal-link sample-feed-response :next))))
  
  (testing "Return nil when link not found"
    (is (nil? (helpers/hal-link sample-story-with-embedded :nonexistent)))
    (is (nil? (helpers/hal-link {} :self)))))

(deftest test-external-url
  (testing "Extract external URL from story"
    (is (= "https://example.com/article"
           (helpers/external-url sample-story-with-embedded)))
    
    (is (= "https://example.com/post"
           (helpers/external-url sample-story-links-only)))))

;; ============================================================================
;; Tests: Smart HAL Navigation Helpers (Phase 2)
;; ============================================================================

(deftest test-fetch-relation-with-embedded
  (testing "Return embedded resource immediately"
    (async done
      (-> (helpers/fetch-relation sample-story-with-embedded :author)
          (.then (fn [author]
                   (is (= "jcs" (:username author)))
                   (is (= 2937 (:karma author)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-fetch-relation-fetches-link
  (testing "Fetch link when resource not embedded"
    (async done
      (-> (helpers/fetch-relation sample-story-links-only :author)
          (.then (fn [author]
                   (is (= "bob" (:username author)))
                   (is (= 500 (:karma author)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-fetch-relation-rejects-missing
  (testing "Reject when relation doesn't exist"
    (async done
      (-> (helpers/fetch-relation sample-story-links-only :nonexistent)
          (.then (fn [_]
                   (is false "Should have rejected")
                   (done)))
          (.catch (fn [err]
                    (is (string? (.-message err)))
                    (is (re-find #"No link or embedded" (.-message err)))
                    (done)))))))

(deftest test-fetch-collection-with-embedded
  (testing "Extract embedded collection items"
    (async done
      (-> (helpers/fetch-relation sample-story-with-embedded :comments)
          (.then (fn [comments]
                   ;; fetch-relation returns the embedded array directly
                   (is (= 2 (count comments)))
                   (is (= "Great article!" (:text (first comments))))
                   (is (= "Thanks for sharing" (:text (second comments))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-fetch-collection-fetches-and-extracts
  (testing "Fetch link and extract collection items"
    (async done
      (-> (helpers/fetch-collection sample-story-links-only :comments)
          (.then (fn [comments]
                   (is (= 3 (count comments)))
                   (is (= "Comment 1" (:text (first comments))))
                   (is (= "Comment 3" (:text (last comments))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-follow-link-always-fetches
  (testing "Follow link always fetches, even if embedded"
    (async done
      (-> (helpers/follow-link sample-story-with-embedded :comments)
          (.then (fn [response]
                   ;; Should fetch the full response, not return embedded
                   (is (contains? response :_links))
                   (is (contains? response :_embedded))
                   (is (= 3 (count (helpers/hal-collection response :comments))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-follow-link-rejects-missing
  (testing "Reject when link doesn't exist"
    (async done
      (-> (helpers/follow-link sample-story-with-embedded :nonexistent)
          (.then (fn [_]
                   (is false "Should have rejected")
                   (done)))
          (.catch (fn [err]
                    (is (string? (.-message err)))
                    (is (re-find #"No link for relation" (.-message err)))
                    (done)))))))

;; ============================================================================
;; Tests: Helper Predicates
;; ============================================================================

(deftest test-has-relation?
  (testing "Return true for embedded relations"
    (is (true? (helpers/has-relation? sample-story-with-embedded :author)))
    (is (true? (helpers/has-relation? sample-story-with-embedded :comments))))
  
  (testing "Return true for link-only relations"
    (is (true? (helpers/has-relation? sample-story-links-only :author)))
    (is (true? (helpers/has-relation? sample-story-links-only :comments)))
    (is (true? (helpers/has-relation? sample-story-links-only :external))))
  
  (testing "Return true for relations that have both link and embedded"
    (is (true? (helpers/has-relation? sample-story-with-embedded :comments))))
  
  (testing "Return false for missing relations"
    (is (false? (helpers/has-relation? sample-story-with-embedded :nonexistent)))
    (is (false? (helpers/has-relation? sample-story-links-only :missing)))
    (is (false? (helpers/has-relation? {} :anything)))))

(deftest test-embedded?
  (testing "Return true for embedded resources"
    (is (true? (helpers/embedded? sample-story-with-embedded :author)))
    (is (true? (helpers/embedded? sample-story-with-embedded :comments))))
  
  (testing "Return false for link-only resources"
    (is (false? (helpers/embedded? sample-story-links-only :author)))
    (is (false? (helpers/embedded? sample-story-links-only :comments))))
  
  (testing "Return false for missing resources"
    (is (false? (helpers/embedded? sample-story-with-embedded :nonexistent)))
    (is (false? (helpers/embedded? {} :anything)))))

(deftest test-paginated?
  (testing "Return true when next link exists"
    (is (true? (helpers/paginated? sample-feed-response))))
  
  (testing "Return true when prev link exists"
    (is (true? (helpers/paginated? sample-feed-last-page))))
  
  (testing "Return false when no pagination links"
    (is (false? (helpers/paginated? sample-story-with-embedded)))
    (is (false? (helpers/paginated? sample-story-links-only)))
    (is (false? (helpers/paginated? {})))))

;; ============================================================================
;; Tests: Pagination Helpers
;; ============================================================================

(deftest test-next-page
  (testing "Fetch next page when available"
    (async done
      (-> (helpers/next-page sample-feed-response)
          (.then (fn [next-response]
                   (is (contains? next-response :_embedded))
                   (is (= 1 (count (helpers/hal-collection next-response :stories))))
                   (is (= "Story 4" (:title (first (helpers/hal-collection next-response :stories)))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-next-page-rejects-when-unavailable
  (testing "Reject when no next page"
    (async done
      (-> (helpers/next-page sample-feed-last-page)
          (.then (fn [_]
                   (is false "Should have rejected")
                   (done)))
          (.catch (fn [err]
                    (is (string? (.-message err)))
                    (is (re-find #"No link for relation" (.-message err)))
                    (done)))))))

(deftest test-prev-page
  (testing "Fetch previous page when available"
    (async done
      (-> (helpers/prev-page sample-feed-last-page)
          (.then (fn [prev-response]
                   ;; Would fetch page 4, but we don't have it mocked
                   ;; Just verify it attempts to follow the link
                   (is false "Mock not set up for page 4")
                   (done)))
          (.catch (fn [err]
                    ;; Expected since we don't have page 4 mocked
                    (is (re-find #"Mock not found" (.-message err)))
                    (done)))))))

;; ============================================================================
;; Integration Tests: Complex Workflows
;; ============================================================================

(deftest test-workflow-fetch-then-navigate
  (testing "Complex workflow: fetch relation then navigate collection"
    (async done
      (-> (helpers/fetch-relation sample-story-links-only :comments)
          (.then (fn [comments-response]
                   ;; First verify we got the full response
                   (is (contains? comments-response :_links))
                   (is (contains? comments-response :_embedded))
                   ;; Then extract and verify collection
                   (let [comments (helpers/hal-collection comments-response :comments)]
                     (is (= 3 (count comments)))
                     (is (= 10 (:id (first comments))))
                     (done))))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))

(deftest test-workflow-check-before-fetch
  (testing "Check if resource exists before fetching"
    (let [story sample-story-with-embedded]
      (when (helpers/has-relation? story :author)
        (if (helpers/embedded? story :author)
          (is (= "jcs" (:username (get-in story [:_embedded :author]))))
          (is false "Author should be embedded")))
      
      (when (helpers/has-relation? story :comments)
        (is (true? (helpers/embedded? story :comments)))
        (is (= 2 (count (helpers/hal-collection story :comments))))))))

(deftest test-workflow-pagination-chain
  (testing "Chain pagination calls"
    (async done
      (-> (helpers/fetch-relation sample-feed-response :self)
          (.then (fn [feed]
                   ;; Verify first page
                   (is (= 3 (count (helpers/hal-collection feed :stories))))
                   (is (true? (helpers/paginated? feed)))
                   ;; Return promise for next page
                   (helpers/next-page feed)))
          (.then (fn [next-feed]
                   ;; Verify second page
                   (is (= 1 (count (helpers/hal-collection next-feed :stories))))
                   (is (= "Story 4" (:title (first (helpers/hal-collection next-feed :stories)))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Should not reject: " err))
                    (done)))))))
