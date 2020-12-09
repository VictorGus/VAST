(ns app.data-grid.model
  (:require [re-frame.core :as rf]
            [app.data-grid.form :as form]))

(def index ::index)

(rf/reg-event-fx
 ::retrieve-data-list
 (fn [{db :db} [_ {:keys [page q]}]]
   (let [data-type (get-in db [index :current-tab])
         uri (cond
               (= data-type :meteo)
               "/meteorological-data"

               (= data-type :sensor)
               "/sensor-data"

               (= data-type :factory)
               "/factory"

               (= data-type :monitor)
               "/monitor"

               :else
               "/meteorological-data")]
     {:xhr/fetch {:uri uri
                  :params  {:page (or page 1)
                            :q q}
                  :success {:event ::show-data}}})))

(rf/reg-event-fx
 ::init-file-modal
 (fn [{db :db} _]
   {:db (-> db
         (assoc-in [index :file-upload :overwrite] false)
         (assoc-in [index :file-upload :content?]  false))}))

(rf/reg-event-fx
 ::overwrite-data
 (fn [{db :db} _]
   {:db (update-in db [index :file-upload :overwrite] not)}))

(rf/reg-event-fx
 ::add-file
 (fn [{db :db} [_ {:keys [file current-tab]}]]
   (let [uri (cond
               (= current-tab :meteo)
               "/meteorological-data/$bulk"

               (= current-tab :sensor)
               "/sensor-data/$bulk"

               (= current-tab :factory)
               "/factory/$bulk"

               (= current-tab :monitor)
               "/monitor/$bulk"

               :else
               "/meteorological-data/$bulk")]
     {:db (-> db
              (assoc-in [index :file-upload :uri] uri)
              (assoc-in [index :file-upload :content?] true)
              (assoc-in [index :file-upload :file] file))})))

(rf/reg-event-fx
 ::upload-file
 (fn [{db :db} [_ efx]]
   (let [{:keys [uri file overwrite content?]} (get-in db [index :file-upload])]
     (if content?
       {:json/fetch (cond-> {:uri  uri
                             :cnt-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                             :method "post"
                             :success {:event ::success-upload}
                             :files file}

                      overwrite
                      (assoc-in [:params :overwrite] true))
        :db (-> db
                (dissoc :modal)
                (assoc-in [index :file-upload :content?] false)
                (assoc-in [index :file-upload :uploading?] true))}
       {:dispatch [:flash/danger {:msg "No file is selected"}]}))))

(rf/reg-event-fx
 ::success-upload
 (fn [{db :db} _]
   {:dispatch-n [[::retrieve-data-list]
                 [:flash/success {:msg "File has been uploaded"}]]
    :db (assoc-in db [index :file-upload :uploading?] false)}))

(rf/reg-event-fx
 ::success-send
 (fn [{db :db} _]
   {:dispatch-n [[:flash/success {:msg "Record has been created"}]
                 [::retrieve-data-list]
                 [::form/init]]
    :db (dissoc db :modal)}))

(rf/reg-event-fx
 ::send-data
 (fn [{db :db} [_ current-tab]]
   (let [uri (cond
               (= current-tab :meteo)
               "/meteorological-data"

               (= current-tab :sensor)
               "/sensor-data"

               (= current-tab :factory)
               "/factory"

               (= current-tab :monitor)
               "/monitor"

               :else
               "/meteorological-data")
         data (form/eval-form db current-tab)]
     {:xhr/fetch {:uri uri
                  :body data
                  :method "post"
                  :success {:event ::success-send}}})))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase {params :params}]]
   (cond
     (= phase :deinit)
     {}
     (= phase :init)
     {:dispatch-n [[::retrieve-data-list {:page (:page params)
                                          :q    (:q params)}]
                   [::form/init]
                   [::init-file-modal]]
      :db (assoc-in db [index :current-tab] :meteo)}
     (= phase :params)
     {:dispatch [::retrieve-data-list {:page (:page params)
                                       :q    (:q params)
                                       :data-type (get-in db [index :current-tab])}]})))

(rf/reg-event-fx
 ::switch-tab
 (fn [{db :db} [_ data-type]]
   (cond-> {:dispatch [::retrieve-data-list {:data-type data-type}]}
     (= :meteo data-type)
     (assoc :db (assoc-in db [index :current-tab] :meteo))

     (= :sensor data-type)
     (assoc :db (assoc-in db [index :current-tab] :sensor))

     (= :monitor data-type)
     (assoc :db (assoc-in db [index :current-tab] :monitor))

     (= :factory data-type)
     (assoc :db (assoc-in db [index :current-tab] :factory)))))

(rf/reg-event-fx
 ::show-data
 (fn [{db :db} [_ {{{data :data} :entry} :data}]]
   {:db (assoc-in db [index :data] data)}))

(rf/reg-event-fx
 ::delete-record
 (fn [{db :db} [_ {:keys [id]}]]
   (let [current-tab (get-in db [index :current-tab])
         rt (cond
              (= :meteo current-tab)
              "meteorological-data"

              (= :sensor current-tab)
              "sensor-data"

              (= :factory current-tab)
              "factory"

              (= :monitor current-tab)
              "monitor")]
     {:xhr/fetch {:uri (str "/" rt "/" id)
                  :success {:event ::retrieve-data-list}
                  :method "DELETE"}
      :dispatch  [:flash/success {:msg (str "Record " id " has been removed!")}]})))

(rf/reg-event-fx
 ::search
 (fn [{db :db} _]
   {:dispatch [:zframes.redirect/merge-params {:q (get-in db [:form :search :value])}]}
   #_{:dispatch-debounce {:delay 1000
                        :key   ::zframes-redirect-merge-params
                        :event [:zframes.routing/merge-params {:q value}]}}))

(rf/reg-sub
 index
 (fn [db]
   (get-in db [index])))
