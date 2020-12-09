(ns app.data-grid.form
  (:require [re-frame.core      :as rf]
            [zenform.model      :as zf]
            [zenform.validators :as validators]))

(def form-path-meteo    [:form :modal :meteo])

(def form-path-sensor   [:form :modal :sensor])

(def form-path-factory  [:form :modal :factory])

(def form-path-monitor  [:form :modal :monitor])

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

(def form-schema-factory
  {:type :form
   :fields {:factory_name {:type :string}
            :longitude    {:type :string}
            :latitude     {:type :string}
            :description  {:type :string}}})

(def form-schema-monitor
  {:type :form
   :fields {:id           {:type :string}
            :longitude    {:type :string}
            :latitude     {:type :string}}})

(def schema-dict {:meteo   form-path-meteo
                  :sensor  form-path-sensor
                  :factory form-path-factory
                  :monitor form-path-monitor})

(rf/reg-event-fx
 ::init
 (fn [{db :db} [_ {:keys [resource]}]]
   {:dispatch-n [[:zf/init form-path-meteo    form-schema-meteo    resource]
                 [:zf/init form-path-sensor   form-schema-sensor   resource]
                 [:zf/init form-path-factory  form-schema-factory  resource]
                 [:zf/init form-path-monitor  form-schema-monitor  resource]]}))

(defn eval-form [db current-tab]
  (let [{:keys [errors form value]} (->> (current-tab schema-dict) (get-in db) zf/eval-form)
        data (reduce-kv
              (fn [acc k v]
                (cond
                  (= :date_ts k)
                  (assoc acc k (str (clojure.string/replace (or (:value v) v) "T" " ") ":00"))

                  :else
                  (assoc acc k (or (:value v) v))))
              {}
              value)]
    data))


(rf/reg-event-fx
 ::deinit
 (fn [{db :db}]
   {:db (-> db
            (assoc-in form-path-meteo nil)
            (assoc-in form-path-sensor nil))}))
