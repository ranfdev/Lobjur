(ns api.protocol)

;; ID Registry - maps public IDs to backend-specific data
(defonce id-registry (atom {}))

(defn register-id!
  "Register a public ID mapping to a backend."
  [public-id backend native-id]
  (swap! id-registry assoc public-id {:backend backend :native-id native-id})
  public-id)

(defn lookup-id
  "Look up backend info for a public ID."
  [public-id]
  (get @id-registry public-id))

(defn make-story-id
  "Create a unique story ID and register it."
  [backend native-id]
  (let [public-id (str (name backend) "-" native-id)]
    (register-id! public-id backend native-id)
    public-id))

;; Protocol for backend adapters

(defprotocol BackendAdapter
  (source-name [this])
  (source-id [this])
  (source-index [this])
  (supports-tags? [this])
  (fetch-feed [this feed-type query])
  (fetch-story [this native-id])
  (fetch-comments [this native-id])
  (fetch-user [this username])
  (fetch-user-stories [this username query])
  (fetch-tags [this])
  (fetch-tag-stories [this tag query])
  (fetch-domain-stories [this domain query]))

;; Adapter Registry

(defonce adapters (atom {}))

(defn register-adapter!
  "Register an adapter by source id."
  [source-id adapter]
  (swap! adapters assoc source-id adapter))

(defn get-adapter
  "Get an adapter by source name/id."
  [source]
  (get @adapters (name source)))
