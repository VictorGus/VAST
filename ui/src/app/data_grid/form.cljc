(ns app.data-grid.form
  (:require [re-frame.core      :as rf]
            [zenform.model      :as zf]
            [zenform.validators :as validators]))

(def form-path-meteo  [:form :modal :meteo])

(def form-path-sensor [:form :modal :sensor])

(def form-schema-meteo
  {:type :form
   :fields {:wind_direction  {:type :string}
            :wind_speed      {:type :string}
            :elevation       {:type :string}
            :date_ts         {:type :string}}})

(def form-schema-sensor
  {:type :form
   :fields {:wind_direction  {:type :string}
            :wind_speed      {:type :string}
            :elevation       {:type :string}
            :date_ts         {:type :string}}})

(def schema-dict {:meteo  form-schema-meteo
                  :sensor form-schema-sensor})

(rf/reg-event-fx
 ::init
 (fn [{db :db} [_ {:keys [resource]}]]
   {:dispatch-n [[:zf/init form-path-meteo form-schema-meteo resource]
                 [:zf/init form-path-sensor form-schema-sensor resource]]}))

(defn eval-form [db current-tab]
  (let [{:keys [errors form value]} (->> (current-tab schema-dict) (get-in db) zf/eval-form)
        data (reduce-kv
              (fn [acc k v]
                (cond
                  (= :date_ts k)
                  (assoc acc k (str (clojure.string/replace (:value v) "T" " ") ":00"))

                  :else
                  (assoc acc k (:value v))))
              {}
              value)]
    (println data)
    data))


(rf/reg-event-fx
 ::deinit
 (fn [{db :db}]
   {:db (-> db
            (assoc-in form-path-meteo nil)
            (assoc-in form-path-sensor nil))}))
