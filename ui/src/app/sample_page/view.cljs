(ns app.sample-page.view
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [app.pages :as pages]
            [app.sample-page.model :as model]))

(pages/reg-subs-page
 model/index
 (fn [_]
   [:div "Hello world"]))
