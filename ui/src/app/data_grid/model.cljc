(ns app.data-grid.model
  (:require [re-frame.core :as rf]
            [app.data-grid.form :as form]))

(def index ::index)

(rf/reg-event-fx
 ::retrieve-data-list
 (fn [{db :db} [_ {:keys [page data-type]}]]
   (let [uri (cond
               (= data-type :meteo)
               "/meteorological-data"

               (= data-type :sensor)
               "/sensor-data"

               :else
               "/meteorological-data")]
     {:xhr/fetch {:uri uri
                  :params  {:page (or page 1)}
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
   {:dispatch [:flash/success {:msg "File has been uploaded"}]
    :db (assoc-in db [index :file-upload :uploading?] false)}))

(rf/reg-event-fx
 ::success-send
 (fn [{db :db} _]
   {:dispatch-n [[:flash/success {:msg "Record has been created"}]
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

               :else
               "/meteorological-data")
         data (form/eval-form db current-tab)]
     {:xhr/fetch {:uri uri
                  :body data
                  :method "post"
                  :success {:event ::success-send}}})))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase params]]
   (cond
     (= phase :deinit)
     {}
     (or params (= phase :init))
     {:dispatch-n [[::retrieve-data-list {:page (-> params
                                                    :params
                                                    :page)}]
                   [::form/init]
                   [::init-file-modal]]
      :db (assoc-in db [index :current-tab] :meteo)})))

(rf/reg-event-fx
 ::switch-tab
 (fn [{db :db} [_ data-type]]
   (cond-> {:dispatch [::retrieve-data-list {:data-type data-type}]}
     (= :meteo data-type)
     (assoc :db (assoc-in db [index :current-tab] :meteo))

     (= :sensor data-type)
     (assoc :db (assoc-in db [index :current-tab] :sensor)))))

(rf/reg-event-fx
 ::show-data
 (fn [{db :db} [_ {{{data :data} :entry} :data}]]
   {:db (assoc-in db [index :data] data)}))

(rf/reg-event-fx
 ::delete-record
 (fn [{db :db} [_ {:keys [id type]}]]
   {:xhr/fetch {:uri (str "/" (name type) "/" id)
                :success {:event ::retrieve-data-list}
                :method "DELETE"}
    :dispatch  [:flash/success {:msg (str "Record " id " has been removed!")}]}))

(rf/reg-sub
 index
 (fn [db]
   (get-in db [index])))
