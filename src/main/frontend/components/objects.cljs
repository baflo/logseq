(ns frontend.components.objects
  "Provides table views for class objects and property related objects"
  (:require [frontend.components.filepicker :as filepicker]
            [frontend.components.views :as views]
            [frontend.db :as db]
            [frontend.db-mixins :as db-mixins]
            [frontend.db.model :as db-model]
            [frontend.handler.editor :as editor-handler]
            [frontend.mixins :as mixins]
            [frontend.state :as state]
            [logseq.db.frontend.property :as db-property]
            [logseq.outliner.property :as outliner-property]
            [logseq.shui.ui :as shui]
            [promesa.core :as p]
            [rum.core :as rum]))

(defn- add-new-class-object!
  [class properties]
  (p/let [block (editor-handler/api-insert-new-block! ""
                                                      {:page (:block/uuid class)
                                                       :properties (merge properties {:block/tags (:db/id class)})
                                                       :edit-block? false})]
    (editor-handler/edit-block! (db/entity [:block/uuid (:block/uuid block)]) 0 {:container-id :unknown-container})
    block))

(defn- build-asset-file-column
  [config]
  {:id :file
   :name "File"
   :type :string
   :header views/header-cp
   :cell (fn [_table row _column]
           (when-let [asset-cp (state/get-component :block/asset-cp)]
             [:div.block-content (asset-cp (assoc config :disable-resize? true) row)]))
   :disable-hide? true})

(rum/defc class-objects-inner < rum/static
  [config class properties]
  (let [;; Properties can be nil for published private graphs
        properties' (remove nil? properties)
        columns* (views/build-columns config properties' {:add-tags-column? true})
        columns (cond
                  (= (:db/ident class) :logseq.class/Pdf-annotation)
                  (remove #(contains? #{:logseq.property/ls-type} (:id %)) columns*)
                  (= (:db/ident class) :logseq.class/Asset)
                  (remove #(contains? #{:logseq.property.asset/checksum} (:id %)) columns*)
                  :else
                  columns*)
        columns (if (= (:db/ident class) :logseq.class/Asset)
                  ;; Insert in front of tag's properties
                  (let [[before-cols after-cols] (split-with #(not (db-property/logseq-property? (:id %))) columns)]
                    (concat before-cols [(build-asset-file-column config)] after-cols))
                  columns)]

    (views/view {:config config
                 :view-parent class
                 :view-feature-type :class-objects
                 :columns columns
                 :add-new-object! (fn [_view table {:keys [properties]}]
                                    (let [set-data! (get-in table [:data-fns :set-data!])
                                          full-data (:full-data table)]
                                      (if (= :logseq.class/Asset (:db/ident class))
                                        (shui/dialog-open!
                                         (fn []
                                           [:div.flex.flex-col.gap-2
                                            [:div.font-medium "Add assets"]
                                            (filepicker/picker
                                             {:on-change (fn [_e files]
                                                           (p/let [entities (editor-handler/upload-asset! nil files :markdown editor-handler/*asset-uploading? true)]
                                                             (shui/dialog-close!)
                                                             (when (seq entities)
                                                               (set-data! (concat full-data (map :db/id entities))))))})]))
                                        (p/let [block (add-new-class-object! class properties)]
                                          (set-data! (conj (vec full-data) (:db/id block)))))))
                 :show-add-property? true
                 :show-items-count? true
                 :add-property! (fn []
                                  (state/pub-event! [:editor/new-property {:block class
                                                                           :class-schema? true}]))})))

(rum/defcs class-objects < rum/reactive db-mixins/query mixins/container-id
  [state class {:keys [current-page? sidebar?]}]
  (when class
    (let [class (db/sub-block (:db/id class))
          config {:container-id (:container-id state)
                  :current-page? current-page?
                  :sidebar? sidebar?}
          properties (outliner-property/get-class-properties class)]
      [:div.ml-1
       (class-objects-inner config class properties)])))

(defn- add-new-property-object!
  [property properties]
  (p/let [default-value (if (= :checkbox (:logseq.property/type property))
                          false
                          (:db/id (db/entity :logseq.property/empty-placeholder)))
          block (editor-handler/api-insert-new-block! ""
                                                      {:page (:block/uuid property)
                                                       :properties (merge
                                                                    {(:db/ident property) default-value}
                                                                    properties)
                                                       :edit-block? false})]
    (editor-handler/edit-block! (db/entity [:block/uuid (:block/uuid block)]) 0 {:container-id :unknown-container})
    block))

(rum/defc property-related-objects-inner < rum/static
  [config property properties]
  (let [columns (views/build-columns config properties)]
    (views/view {:config config
                 :view-parent property
                 :view-feature-type :property-objects
                 :columns columns
                 :add-new-object! (fn [_view table {:keys [properties]}]
                                    (p/let [set-data! (get-in table [:data-fns :set-data!])
                                            full-data (:full-data table)
                                            block (add-new-property-object! property properties)]
                                      (set-data! (conj (vec full-data) (:db/id block)))))
                 ;; TODO: Add support for adding column
                 :show-add-property? false})))

;; Show all nodes containing the given property
(rum/defcs property-related-objects < rum/reactive db-mixins/query mixins/container-id
  [state property current-page?]
  (when property
    (let [property' (db/sub-block (:db/id property))
          config {:container-id (:container-id state)
                  :current-page? current-page?}
          ;; Show tags to help differentiate property rows
          properties [property' (db/entity :block/tags)]]
      [:div.ml-1
       (property-related-objects-inner config property' properties)])))
