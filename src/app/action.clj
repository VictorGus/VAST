(ns app.action
  (:require [app.dbcore    :as db]
            [clojure.edn   :as edn]
            [honeysql.core :as hsql]))

(defn get-measured-chemicals [{connection :db/connection :as ctx}]
  (fn [req]
    (let [query {:select [:chemical (hsql/raw "sum(reading::float)")]
                 :from [:sensor_data]
                 :group-by [:chemical]}
          res (db/query query connection)]
      {:status 200
       :body res})))

(defn move-point [angle base point]
  (let [{xb :x yb :y} base
        {xp :x yp :y} point
        x' (- (+ xb (* (- xp xb)  (Math/cos (Math/toRadians angle)))) (* (- yp yb)  (Math/sin (Math/toRadians angle))))
        y' (- (+ yb (* (- xp xb)  (Math/sin (Math/toRadians angle)))) (* (- yp yb)  (Math/cos (Math/toRadians angle))))]
    [x' y']))

(defn get-coverage-area [{:keys [wind-speed longitude latitude]}]
  (let [default-side-size 4
        squares [{:top-right-corner {:x (+ longitude (* default-side-size
                                                        (/ wind-speed 2)))
                                     :y (+ latitude (* default-side-size
                                                       (/ wind-speed 2)))}
                  :top-left-corner  {:x longitude
                                     :y (+ latitude (* default-side-size
                                                       (/ wind-speed 2)))}
                  :bottom-left-corner {:x longitude
                                       :y latitude}
                  :bottom-right-corner {:x (+ longitude (* default-side-size
                                                           (/ wind-speed 2)))
                                        :y latitude}}

                 {:top-right-corner {:x longitude
                                     :y (+ latitude (* default-side-size
                                                       (/ wind-speed 2)))}
                  :top-left-corner  {:x (- longitude (* default-side-size
                                                        (/ wind-speed 2)))
                                     :y (+ latitude (* default-side-size
                                                       (/ wind-speed 2)))}
                  :bottom-left-corner {:x (- longitude (* default-side-size
                                                          (/ wind-speed 2)))
                                       :y latitude}
                  :bottom-right-corner {:x longitude
                                        :y latitude}}

                 {:top-right-corner {:x (+ longitude (* default-side-size
                                                        (/ wind-speed 2)))
                                     :y latitude}
                  :top-left-corner  {:x longitude
                                     :y latitude}
                  :bottom-left-corner {:x longitude
                                       :y (- latitude  (* default-side-size
                                                          (/ wind-speed 2)))}
                  :bottom-right-corner {:x (+ longitude (* default-side-size
                                                           (/ wind-speed 2)))
                                        :y (- latitude (* default-side-size
                                                          (/ wind-speed 2)))}}

                 {:top-right-corner {:x longitude
                                     :y latitude}
                  :top-left-corner  {:x (- longitude (* default-side-size
                                                        (/ wind-speed 2)))
                                     :y latitude}
                  :bottom-left-corner {:x (- longitude (* default-side-size
                                                          (/ wind-speed 2)))
                                       :y (- latitude  (* default-side-size
                                                          (/ wind-speed 2)))}
                  :bottom-right-corner {:x longitude
                                        :y (- latitude  (* default-side-size
                                                           (/ wind-speed 2)))}}]]
    squares))

(defn sum-coords [x y]
  (+ (Math/abs x) (Math/abs y)))

(defn factory-within-area? [{:keys [longitude latitude] :as factory}
                            {:keys [top-right-corner top-left-corner
                                    bottom-left-corner bottom-right-corner] :as area}]
  (let [longitude (edn/read-string longitude)
        latitude  (edn/read-string latitude)]

    (and (<= longitude (:x top-right-corner)) (>= longitude (:x top-left-corner))
         (<= latitude  (:y top-right-corner)) (>= latitude  (:y bottom-right-corner)))))

(defn get-closest-factory [{xs :longitude ys :latitude :as sensor} factories]
  (let [xs (edn/read-string xs)
        ys (edn/read-string ys)
        sensor-coords-sum (sum-coords xs ys)
        factories (map
                   #(assoc % :distance (Math/abs (- (sum-coords (edn/read-string (:longitude %))
                                                                (edn/read-string (:latitude %)))
                                                    sensor-coords-sum)))
                   factories)]
    (->> factories
         (sort-by :distance)
         first)))

(defn find-factory [{:keys [longitude latitude] :as sensor}
                    wind-direction wind-speed factories]
  (let [coverage-area (get-coverage-area {:wind-speed (edn/read-string wind-speed)
                                          :longitude (edn/read-string longitude)
                                          :latitude (edn/read-string  latitude)})
        wind-direction (edn/read-string wind-direction)
        possible-area (cond
                        (and (>= wind-direction 0) (<= wind-direction 90))
                        (last coverage-area)

                        (and (>= wind-direction 90) (<= wind-direction 180))
                        (second coverage-area)

                        (and (>= wind-direction 180) (<= wind-direction 270))
                        (first coverage-area)

                        (and (>= wind-direction 270) (<= wind-direction 360))
                        (nth coverage-area 2))]
    (->> factories
         (filter
          #(factory-within-area? % possible-area))
         (get-closest-factory sensor))))

(defn get-polluting-factories [{connection :db/connection :as ctx}]
  (fn [{{:keys [factory_name]} :params :as request}]
    (let [res (db/query {:select [[:sd.date_ts   :date_ts]
                                  [:mt.longitude :longitude]
                                  [:mt.latitude  :latitude]
                                  [:md.direction :wind_direction]
                                  [:md.speed     :wind_speed]
                                  [:sd.reading   :reading]]
                         :from [[:sensor_data :sd]]
                         :join [[:monitor :mt] [:= :mt.id :sd.monitor]]
                         :left-join [[:meteo_data :md] [:= :sd.date_ts :md.date_ts]]
                         :order-by [:sd.date_ts]
                         :where (hsql/raw "md.date_ts is not null")} connection)
          factories (db/query {:select [:*]
                               :from [:factory]} connection)]
      {:status 200
       :body (distinct
              (filter #(= (:factory %) factory_name) (map
                                                      #(assoc % :factory (:factory_name
                                                                          (find-factory (select-keys % [:longitude :latitude])
                                                                                        (:wind_direction %)
                                                                                        (:wind_speed %)
                                                                                        factories)))
                                                      res)))})))
