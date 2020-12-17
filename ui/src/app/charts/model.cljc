(ns app.charts.model
  (:require [re-frame.core :as rf]))

(def index ::index)

(rf/reg-sub
 index
 (fn [db]
   (get-in db [index])))

(rf/reg-event-fx
 ::save-data
 (fn [{db :db} [_ {:keys [data]}]]
   {:db (-> db
            (assoc-in [index :data]    data)
            (assoc-in [index :loaded?] true))}))

(rf/reg-event-fx
 ::retrieve-data
 (fn [{db :db} _]
   {:xhr/fetch {:uri "/$measured-chemicals"
                :success {:event ::save-data}}}))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase {params :params}]]
   (cond
     (= phase :deinit)
     {}
     (= phase :init)
     {:dispatch [::retrieve-data]})))
