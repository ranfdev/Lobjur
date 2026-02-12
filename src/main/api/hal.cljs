(ns api.hal
  (:require [api.url :as url]
            [clojure.string :as str]))

(defn- add-scheme
  "Add in-process scheme to internal paths.
   External links (http:// or https://) remain unchanged."
  [href]
  (if (or (str/starts-with? href "http://")
          (str/starts-with? href "https://"))
    href
    (url/make-url :in-process href)))

(defn link
  "Create a HAL link with proper scheme.
   Internal links get 'in-process://' prefix.
   External links remain unchanged."
  ([href] (link href {}))
  ([href opts]
   (merge {:href (add-scheme href)} opts)))

(defn self-link
  "Create a self link."
  [href]
  {:self (link href)})

(defn resource
  "Create a HAL resource with links and optional embedded resources.
   
   Usage:
   (resource {:self \"/stories/123\"}
             {:title \"My Story\"}
             {:comments [...]})"
  ([links] (resource links {} {}))
  ([links data] (resource links data {}))
  ([links data embedded]
   (cond-> {:_links (if (contains? links :self)
                      links
                      (merge (self-link (:self links)) links))}
     (seq data) (merge data)
     (seq embedded) (assoc :_embedded embedded))))

(defn collection
  "Create a HAL collection resource with embedded items."
  [self-href embed-key items & {:keys [links extra]}]
  (resource (merge {:self (link self-href)} links)
            (or extra {})
            {embed-key items}))

(defn story-links
  "Generate standard links for a story."
  [provider native-id & {:keys [external-url author tags domain]}]
  (cond-> {:self     (link (str "/" provider "/stories/" native-id))
           :comments (link (str "/" provider "/stories/" native-id "/comments"))}
    author       (assoc :author (link (str "/" provider "/users/" author)))
    external-url (assoc :external (link external-url))
    domain       (assoc :domain-stories (link (str "/" provider "/domains/" domain "/stories")))
    (seq tags)   (assoc :tag-stories (mapv (fn [t] (assoc (link (str "/" provider "/tags/" t "/stories")) :name t)) tags))))

(defn comment-links
  "Generate standard links for a comment.
   :replies is only added when the comment actually has replies (links = capabilities)."
  [provider comment-id & {:keys [author replies]}]
  (cond-> {:self (link (str "/" provider "/comments/" comment-id))}
    replies (assoc :replies (link (str "/" provider "/comments/" comment-id "/replies")))
    author  (assoc :author (link (str "/" provider "/users/" author)))))

(defn user-links
  "Generate standard links for a user."
  [provider username]
  {:self    (link (str "/" provider "/users/" username))
   :stories (link (str "/" provider "/users/" username "/stories"))})

(defn paginated
  "Add pagination links to a resource, preserving existing query parameters.
   
   Parameters:
   - resource: The HAL resource to add pagination links to
   - base-href: The base URL path (without query params)
   - page: Current page number
   - query-params: (optional) Map of query parameters to preserve
   - has-next: Whether there is a next page
   - has-prev: Whether there is a previous page"
  [resource base-href page & {:keys [has-next has-prev query-params]}]
  (let [add-link (fn [r rel page-num]
                   (let [params (assoc (or query-params {}) :page page-num)
                         href (url/make-url :in-process base-href params)]
                     (assoc-in r [:_links rel] {:href href})))]
    (cond-> resource
      has-prev (add-link :prev (dec page))
      has-next (add-link :next (inc page)))))
