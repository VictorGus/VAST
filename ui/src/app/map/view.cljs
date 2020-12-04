(ns app.map.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [clojure.string :as str]
            [app.pages :as pages]
            [app.styles :as styles]
            [app.map.model :as model]))


(defn map-page [data]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (js/Datamap. (clj->js {:element (.getElementById js/document "container")
                             :scope "usa"
                             :geographyConfig {:borderColor "black"
                                               :borderWidth "0.5"
                                               :popupOnHover false
                                               :highlightOnHover false}
                             :fills {:defaultFill "white"}})))
    :reagent-render
    (fn [data]
      [:div.container
       [:div#container {:style {:min-height "450px"}}]])}))

(pages/reg-subs-page
 model/index
 (fn [data]
   [map-page data]))

