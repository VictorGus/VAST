(ns app.map.model
  (:require [re-frame.core :as rf]))

(def index ::index)

(def mini-db (atom {}))

(rf/reg-sub
 index
 (fn [db]
   (get-in db [index :data])))


(rf/reg-event-fx
 ::retrieve-monitors
 (fn [{db :db} _]
   {:xhr/fetch {:uri "/monitor"
                :success {:event ::save-data
                          :params {:type :monitors}}}}))

(rf/reg-event-fx
 ::retrieve-factories
 (fn [{db :db} _]
   {:xhr/fetch {:uri "/factory"
                :success {:event ::save-data
                          :params {:type :factories}}}}))

(rf/reg-event-fx
 ::save-data
 (fn [{db :db} [_ {{{data :data} :entry} :data {{{:keys [type]} :params} :success} :request}]]
   (swap! mini-db assoc type data)
   {:db (assoc-in db [index :data type] data)}))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase {params :params}]]
   (cond
     (= phase :deinit)
     {}
     (= phase :init)
     {:dispatch-n [[::retrieve-monitors]
                   [::retrieve-factories]]})))
