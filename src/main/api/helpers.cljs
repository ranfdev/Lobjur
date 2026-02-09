(ns api.helpers
  "Essential helpers for working with HAL API responses."
  (:require [api.router :as router]
            [api.server :as s]))

;; ============================================================================
;; Basic HAL Extraction Helpers (Internal Use)
;; ============================================================================
;; Note: These are low-level helpers. Application code should use the smart
;; navigation helpers below (fetch-collection, fetch-relation) which properly
;; follow HATEOAS principles by checking _embedded first, then fetching links.

(defn hal-collection
  "Extract an embedded collection from a HAL response.
   
   INTERNAL USE ONLY - Application code should use fetch-collection instead,
   which properly handles both embedded and linked resources.
   
   This helper directly accesses _embedded, which violates HATEOAS if the
   collection is not embedded but available via link. Use fetch-collection
   for proper hypermedia navigation.
   
   Examples:
     (hal-collection response :stories)
     (hal-collection response :comments)
     (hal-collection response :feeds)"
  [response collection-key]
  (or (get-in response [:_embedded collection-key]) []))

(defn hal-link
  "Extract a link href from a HAL response.
   
   Common relations: :self, :external, :next, :prev, :author, :comments
   
   Examples:
     (hal-link story :external)
     (hal-link response :next)"
  [response rel]
  (get-in response [:_links rel :href]))

(defn external-url
  "Get the external URL from a story object."
  [story]
  (hal-link story :external))

;; ============================================================================
;; Smart HAL Navigation Helpers (Phase 2)
;; ============================================================================

(defn follow-link
  "Follow a specific link relation.
   Always fetches, even if embedded (use fetch-relation for smart behavior).
   
   Returns: Promise resolving to the resource
   
   Example:
     (follow-link response :next)
     => Promise<{:_embedded {:stories [...]}}>"
  [resource rel]
  (if-let [href (get-in resource [:_links rel :href])]
    (s/GET router/server href)
    (js/Promise.reject
      (js/Error. (str "No link for relation: " rel)))))

(defn fetch-relation
  "Fetch a HAL relation intelligently:
   1. If resource is embedded, return it immediately (wrapped in promise)
   2. Otherwise, fetch the link
   
   Returns: Promise resolving to the resource
   
   Examples:
     ;; Resource is embedded - returns immediately
     (fetch-relation story :author)
     => Promise<{:username 'jcs' :karma 2937 ...}>
     
     ;; Resource not embedded - fetches link
     (fetch-relation story :comments)
     => Promise<{:_embedded {:comments [...]}}>"
  [resource rel]
  (if-let [embedded (get-in resource [:_embedded rel])]
    (js/Promise.resolve embedded)
    (if-let [href (get-in resource [:_links rel :href])]
      (s/GET router/server href)
      (js/Promise.reject
        (js/Error. (str "No link or embedded resource for relation: " rel))))))

(defn fetch-collection
  "Fetch a collection from a HAL resource, checking _embedded first, then _links.
   Optionally provide a default value to return when neither exists."
  ([resource rel] (fetch-collection resource rel nil))
  ([resource rel {:keys [default]}]
   (if-let [embedded-coll (get-in resource [:_embedded rel])]
     (js/Promise.resolve embedded-coll)
     (if (get-in resource [:_links rel :href])
       (-> (follow-link resource rel)
           (.then #(hal-collection % rel)))
       (if (some? default)
         (js/Promise.resolve default)
         (js/Promise.reject (js/Error. (str "No link for relation: " (name rel)))))))))

;; ============================================================================
;; Helper Predicates
;; ============================================================================

(defn has-relation?
  "Check if a resource has a relation (either link or embedded).
   
   Examples:
     (has-relation? story :comments) => true
     (has-relation? story :author) => true
     (has-relation? story :nonexistent) => false"
  [resource rel]
  (or (contains? (:_embedded resource) rel)
      (contains? (:_links resource) rel)))

(defn placeholder?
  "Check if a HAL resource is a placeholder that needs fetching."
  [resource]
  (:_placeholder resource))

(defn embedded?
  "Check if a relation is embedded (not just linked).
   
   Example:
     (embedded? story :author) => false  ; needs fetch
     (embedded? story :comments) => true  ; already loaded"
  [resource rel]
  (contains? (:_embedded resource) rel))

(defn paginated?
  "Check if a resource has pagination links.
   
   Example:
     (paginated? feed-response) => true"
  [resource]
  (or (contains? (:_links resource) :next)
      (contains? (:_links resource) :prev)))

;; ============================================================================
;; Pagination Helpers
;; ============================================================================

(defn next-page
  "Fetch the next page if available.
   
   Returns: Promise resolving to next page, or rejected promise if no next page
   
   Example:
     (-> (router/GET '/feeds/lobsters?page=1')
         (.then next-page)
         (.then #(hal-collection % :stories)))"
  [resource]
  (follow-link resource :next))

(defn prev-page
  "Fetch the previous page if available."
  [resource]
  (follow-link resource :prev))
