(ns api.helpers
  "Essential helpers for working with HAL API responses.")

(defn hal-collection
  "Extract an embedded collection from a HAL response.
   
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
