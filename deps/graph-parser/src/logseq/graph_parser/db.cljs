(ns logseq.graph-parser.db
  "File graph specific db fns"
  (:require [clojure.string :as string]
            [datascript.core :as d]
            [logseq.common.util :as common-util]
            [logseq.common.uuid :as common-uuid]
            [logseq.db :as ldb]
            [logseq.db.file-based.builtins :as file-builtins]
            [logseq.db.file-based.schema :as file-schema]))

(defonce built-in-markers file-builtins/built-in-markers)
(defonce built-in-priorities file-builtins/built-in-priorities)
(defonce built-in-pages-names file-builtins/built-in-pages-names)

(defn- page-title->block
  [title]
  {:block/name (string/lower-case title)
   :block/title title
   :block/uuid (common-uuid/gen-uuid)
   :block/type "page"})

(def built-in-pages
  (mapv page-title->block built-in-pages-names))

(defn- build-pages-tx
  [pages]
  (let [time' (common-util/time-ms)]
    (map
     (fn [m]
       (-> m
           (assoc :block/created-at time')
           (assoc :block/updated-at time')))
     pages)))

(defn create-default-pages!
  "Creates default pages if one of the default pages does not exist. This
   fn is idempotent"
  [db-conn]
  (when-not (ldb/get-page @db-conn "card")
    (let [built-in-pages' (build-pages-tx built-in-pages)]
      (ldb/transact! db-conn built-in-pages'))))

(defn start-conn
  "Create datascript conn with schema and default data"
  []
  (let [db-conn (d/create-conn file-schema/schema)]
    (create-default-pages! db-conn)
    db-conn))

(defn get-page-file
  [db page-name]
  (some-> (ldb/get-page db page-name)
          :block/file))
