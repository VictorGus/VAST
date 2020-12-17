(ns app.charts.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.charts.model :as model]))

(defn chart [{:keys [data]}]
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

(pages/reg-subs-page
 model/index
 (fn [data]
   [:div.container
    (when (:loaded? data)
      [chart data])]))
