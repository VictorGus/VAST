(ns app.data-grid.model
  (:require [re-frame.core :as rf]))

(def index ::index)

(rf/reg-event-fx
 ::retrieve-data-list
 (fn [{db :db} _]
   {:xhr/fetch {:uri "/meteorological-data"
                :success {:event ::show-data}}}))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase]]
   (cond
     (= phase :deinit)
     {}
     (= phase :init)
     {:dispatch [::retrieve-data-list]})))


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
   (get-in db [index :data])))
