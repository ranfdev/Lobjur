(ns api.protocol)

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
