(ns app.charts.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.charts.model :as model]))

(defn chart [data]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [ctx (.getContext (.getElementById js/document "chart") "2d")]
        (new js/Chart ctx (clj->js {:type "bar"
                                    :data {:labels ["Green" "Green" "Green"
                                                    "Green" "Green" "Green"]
                                           :datasets [{:label "# of Votes"
                                                       :data [12 19 3 5 2 3]
                                                       :backgroundColor ["rgba(255, 99, 132, 0.2)"
                                                                         "rgba(54, 162, 235, 0.2)"
                                                                         "rgba(255, 255, 86, 0.2)"
                                                                         "rgba(75, 192, 192, 0.2)"
                                                                         "rgba(153, 102, 255, 0.2)"
                                                                         "rgba(255, 159, 64, 0.2)"]}]}
                                    :options {:scales {:yAxes [{:ticks {:beginAtZero true}}]}}}))))
    :reagent-render
    (fn [data]
      [:canvas#chart])}))

(pages/reg-subs-page
 model/index
 (fn [data]
   [:div.container
    [chart data]]))
