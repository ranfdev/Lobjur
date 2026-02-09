(ns api.sources)

(def sources
  [{:name "Lobsters"
    :id "lobsters"
    :extra-links {:tags "/feeds/lobsters/tags"}
    :feeds [{:title "Hottest" :id "hot" :icon "power-profile-performance-symbolic" :rel :hot}
            {:title "Active" :id "newest" :icon "audio-speakers-symbolic" :rel :newest}]}
   {:name "Hacker News"
    :id "hackernews"
    :extra-links {:search "/hackernews/search"}
    :feeds [{:title "Top" :id "top" :icon "starred-symbolic" :rel :top}
            {:title "New" :id "newest" :icon "document-new-symbolic" :rel :newest}
            {:title "Best" :id "best" :icon "emoji-flags-symbolic" :rel :best}]}])
