(ns app.charts.model
  (:require [re-frame.core :as rf]))

(def index ::index)

(rf/reg-sub
 index
 (fn [db]
   {:data (get-in db [index])
    :chart-type (-> db :fragment-params :params)}))

(rf/reg-event-fx
 ::save-data
 (fn [{db :db} [_ {:keys [data]}]]
   {:db (-> db
            (assoc-in [index :data]    data)
            (assoc-in [index :loaded?] true))}))

(rf/reg-event-fx
 ::save-factories
 (fn [{db :db} [_ {{{data :data} :entry} :data}]]
   {:db (assoc-in db [index :factories] data)}))

(rf/reg-event-fx
 ::retrieve-data
 (fn [{db :db} [_ params]]
   {:xhr/fetch (cond-> {:success {:event ::save-data}}
                 (:type params)
                 (assoc :uri "/$measured-chemicals")

                 (:factory_name params)
                 (assoc :uri "/$polluting-factories"
                        :params params))}))

(rf/reg-event-fx
 ::retrieve-factories
 (fn [{db :db} _]
   {:xhr/fetch {:uri "/factory"
                :success {:event ::save-factories}}}))

(rf/reg-event-fx
 index
 (fn [{db :db} [pid phase {params :params}]]
   (cond
     (= phase :deinit)
     {}

     (= phase :init)
     {:zframes.redirect/redirect {:uri (get db :fragment-path)
                                  :params {:type "chemicals"}}
      :dispatch-n [[::retrieve-data {:type "chemicals"}]
                   [::retrieve-factories]]}

     params
     {:dispatch [::retrieve-data params]
      :db (assoc-in db [index :loaded?] false)})))
