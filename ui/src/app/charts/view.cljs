(ns app.charts.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.charts.model :as model]
            [zframes.redirect :as redirect]))

(defn bar-chart [{:keys [data]}]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [ctx  (.getContext (.getElementById js/document "chart") "2d")
            data (map #(assoc % :color (str "#"
                                            (.toString
                                             (.floor js/Math (* 16777215 (.random js/Math)))
                                             16)))
                      data)]
        (new js/Chart ctx (clj->js {:type "bar"
                                    :data {:labels (mapv :chemical data)
                                           :datasets [{:label "Chemical"
                                                       :data (mapv :sum data)
                                                       :backgroundColor (mapv :color data)}]}
                                    :options {:scales {:yAxes [{:ticks {:beginAtZero true}}]}}}))))
    :reagent-render
    (fn [data]
      [:canvas#chart])}))

(defn line-chart [{:keys [data]}]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [ctx  (.getContext (.getElementById js/document "chart") "2d")]
        (new js/Chart ctx (clj->js {:type "line"
                                    :data {:labels (mapv #(first (clojure.string/split (:date_ts %) "T")) data)
                                           :datasets [{:label (:factory (first data))
                                                       :data  (mapv #(js/parseFloat (:reading %)) data)}]}
                                    :options {:scales {:yAxes [{:ticks {:beginAtZero true}}]}}}))))
    :reagent-render
    (fn [data]
      [:canvas#chart])}))

(pages/reg-subs-page
 model/index
 (fn [{:keys [data chart-type]}]
   [:div.container
    [:div.mb-3
     [:select.custom-select.w-25
      {:onChange (fn [e]
                   (let [data (-> e .-target .-value)]
                     (rf/dispatch [::redirect/set-params (if (= "chemicals" data)
                                                           {:type "chemicals"}
                                                           {:factory_name data})])))}
      (cons
       [:option {:value "chemicals"}
        "Measured chemicals"]
       (for [factory (:factories data)]
         [:option {:value (:factory_name factory)}
          (:factory_name factory)]))]]
    (if (:loaded? data)
      [:<>
       (cond
         (:type chart-type)
         [bar-chart data]

         :else
         [line-chart data])]
      [:div.text-center {:style {:margin "50px"}}
       [:div.spinner-border.text-primary {:role "status"
                                          :style {:width  "5rem"
                                                  :height "5rem"}}
        [:span.sr-only "Loading..."]]])]))
