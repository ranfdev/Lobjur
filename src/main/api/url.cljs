(ns api.url
  "URL parsing and construction utilities for handling both in-process:// and https:// schemes.
   
   This namespace provides utilities to parse and construct URLs that support:
   - in-process:///... - Internal API routes handled by the router
   - https://... - External HTTP/HTTPS resources
   - Relative paths (default to in-process scheme)"
  (:require [clojure.string :as str]))

;; URL Parsing

(defn parse-url
  "Parse a URL string into its components.
   
   Returns a map with:
   - :scheme - :in-process, :https, or :http
   - :path - The path component (for in-process) or full URL (for https)
   - :query - Optional query string (raw)
   - :query-params - Parsed query parameters as a map
   
   Examples:
     (parse-url \"in-process:///lobsters/feeds\")
     => {:scheme :in-process 
         :path \"/lobsters/feeds\" 
         :query nil 
         :query-params {}}
     
     (parse-url \"in-process:///stories/123?format=json&embed=comments\")
     => {:scheme :in-process 
         :path \"/stories/123\" 
         :query \"format=json&embed=comments\"
         :query-params {:format \"json\" :embed \"comments\"}}
     
     (parse-url \"https://example.com/page?id=1\")
     => {:scheme :https 
         :path \"https://example.com/page\"
         :query \"id=1\"
         :query-params {:id \"1\"}}
     
     (parse-url \"/lobsters/feeds/hot\")
     => {:scheme :in-process 
         :path \"/lobsters/feeds/hot\" 
         :query nil
         :query-params {}}"
  [url]
  (when-not (string? url)
    (throw (js/Error. (str "URL must be a string, got: " (type url)))))
  
  (when (str/blank? url)
    (throw (js/Error. "URL cannot be empty")))
  
  (cond
    ;; HTTPS or HTTP URLs
    (or (str/starts-with? url "https://")
        (str/starts-with? url "http://"))
    (let [scheme (if (str/starts-with? url "https://") :https :http)
          [path-part query] (str/split url #"\?" 2)
          query-params (when query
                         (into {}
                               (for [pair (str/split query #"&")
                                     :let [[k v] (str/split pair #"=" 2)]
                                     :when k]
                                 [(keyword k) (or v "")])))]
      {:scheme scheme
       :path path-part
       :query query
       :query-params (or query-params {})})
    
    ;; in-process:// URLs
    (str/starts-with? url "in-process://")
    (let [without-scheme (subs url 13) ; Remove "in-process://"
          ;; Backward compatibility for old in-process://api/... links
          without-scheme (if (re-find #"^api(?:/|\?|$)" without-scheme)
                           (subs without-scheme 3)
                           without-scheme)
          [path-part query] (str/split without-scheme #"\?" 2)
          ;; Ensure path starts with /
          normalized-path (if (str/blank? path-part)
                            "/"
                            (if (str/starts-with? path-part "/")
                              path-part
                              (str "/" path-part)))
          ;; Remove trailing slash if present (except for root "/")
          normalized-path (if (and (> (count normalized-path) 1)
                                   (str/ends-with? normalized-path "/"))
                            (subs normalized-path 0 (dec (count normalized-path)))
                            normalized-path)
          query-params (when query
                         (into {}
                               (for [pair (str/split query #"&")
                                     :let [[k v] (str/split pair #"=" 2)]
                                     :when k]
                                 [(keyword k) (or v "")])))]
      {:scheme :in-process
       :path normalized-path
       :query query
       :query-params (or query-params {})})
    
    ;; Relative paths - default to in-process
    :else
    (let [[path-part query] (str/split url #"\?" 2)
          ;; Ensure path starts with /
          normalized-path (if (str/starts-with? path-part "/")
                            path-part
                            (str "/" path-part))
          ;; Remove trailing slash if present (except for root "/")
          normalized-path (if (and (> (count normalized-path) 1)
                                   (str/ends-with? normalized-path "/"))
                            (subs normalized-path 0 (dec (count normalized-path)))
                            normalized-path)
          query-params (when query
                         (into {}
                               (for [pair (str/split query #"&")
                                     :let [[k v] (str/split pair #"=" 2)]
                                     :when k]
                                 [(keyword k) (or v "")])))]
      {:scheme :in-process
       :path normalized-path
       :query query
       :query-params (or query-params {})})))

;; URL Construction

(defn make-url
  "Construct a URL with the specified scheme and path.
   
   Parameters:
   - scheme: :in-process, :https, or :http
   - path: The path component (should start with / for in-process)
   - query-params: (optional) Map of query parameters
   
   Examples:
     (make-url :in-process \"/lobsters/feeds\")
     => \"in-process:///lobsters/feeds\"
     
     (make-url :in-process \"/stories/123\" {:format \"json\" :embed \"comments\"})
     => \"in-process:///stories/123?format=json&embed=comments\"
     
     (make-url :https \"https://example.com/page\")
     => \"https://example.com/page\"
     
     (make-url :https \"https://example.com/page\" {:id \"1\"})
     => \"https://example.com/page?id=1\""
  ([scheme path]
   (make-url scheme path nil))
  
  ([scheme path query-params]
   (when-not (keyword? scheme)
     (throw (js/Error. (str "Scheme must be a keyword, got: " (type scheme)))))
   
   (when-not (string? path)
     (throw (js/Error. (str "Path must be a string, got: " (type path)))))
   
   (when (str/blank? path)
     (throw (js/Error. "Path cannot be empty")))
   
   (let [query-string (when (and query-params (seq query-params))
                        (str/join "&"
                                  (for [[k v] query-params]
                                    (str (name k) "=" v))))
         base-url (case scheme
                    :in-process (str "in-process://"
                                     (if (str/starts-with? path "/")
                                       path
                                       (str "/" path)))
                    :https path  ; For https, path is the full URL
                    :http path   ; For http, path is the full URL
                    (throw (js/Error. (str "Unsupported scheme: " scheme))))]
     (if query-string
       (str base-url "?" query-string)
       base-url))))

;; Helper Functions

(defn in-process?
  "Check if a URL uses the in-process scheme.
   
   Examples:
     (in-process? \"in-process:///lobsters/feeds\") => true
     (in-process? \"/lobsters/feeds\") => true
     (in-process? \"https://example.com\") => false"
  [url]
  (= :in-process (:scheme (parse-url url))))

(defn external?
  "Check if a URL is an external HTTP/HTTPS URL.
   
    Examples:
      (external? \"https://example.com\") => true
      (external? \"http://example.com\") => true
      (external? \"in-process:///lobsters\") => false
      (external? \"/lobsters\") => false"
  [url]
  (let [scheme (:scheme (parse-url url))]
    (or (= scheme :https) (= scheme :http))))

(defn normalize-url
  "Normalize a URL by parsing and reconstructing it.
   This ensures consistent formatting and handles relative paths.
   
   Examples:
     (normalize-url \"/lobsters/feeds\")
     => \"in-process:///lobsters/feeds\"
     
     (normalize-url \"in-process:///lobsters/feeds/\")
     => \"in-process:///lobsters/feeds\"
     
     (normalize-url \"https://example.com/page\")
     => \"https://example.com/page\""
  [url]
  (let [{:keys [scheme path query-params]} (parse-url url)]
    (make-url scheme path query-params)))

(defn add-query-params
  "Add query parameters to a URL.
   
   Examples:
     (add-query-params \"/lobsters/feeds\" {:page \"2\" :limit \"20\"})
     => \"in-process:///lobsters/feeds?page=2&limit=20\"
     
     (add-query-params \"https://example.com/api\" {:key \"abc\"})
     => \"https://example.com/api?key=abc\""
  [url params]
  (let [{:keys [scheme path query-params]} (parse-url url)
        merged-params (merge query-params params)]
    (make-url scheme path merged-params)))

(defn get-path
  "Extract the path component from a URL.
   For in-process URLs, returns the path without scheme.
   For external URLs, returns the full URL.
   
   Examples:
     (get-path \"in-process:///lobsters/feeds\") => \"/lobsters/feeds\"
     (get-path \"/lobsters/feeds\") => \"/lobsters/feeds\"
     (get-path \"https://example.com/page\") => \"https://example.com/page\""
  [url]
  (:path (parse-url url)))

(defn get-query-params
  "Extract query parameters from a URL.
   
   Examples:
     (get-query-params \"in-process:///stories/123?format=json\")
     => {:format \"json\"}"
  [url]
  (:query-params (parse-url url)))
