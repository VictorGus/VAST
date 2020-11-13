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
 index
 (fn [{db :db} [pid phase params]]
   (cond
     (= phase :deinit)
     {}
     (or params (= phase :init))
     {:dispatch-n [[::retrieve-data-list {:page (-> params
                                                    :params
                                                    :page)}]
                   [::form/init]]
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
