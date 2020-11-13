(ns app.modal
  (:require [re-frame.core :as rf]
            [zframes.modal :as modal]))


(defn modal []
  (let [modal* (rf/subscribe [::modal/modal])]
    (fn []
      (when-let [modal @modal*]
        [:div {:style {:position "fixed"
                       :height "100%"
                       :top 0
                       :margin-top "50px"
                       :width "100%"
                       :background-color "rgba(0, 0, 0, 0.5)"
                       :z-index "99"}
               :on-click (when-not (:persistent modal) #(rf/dispatch [::modal/close-modal]))}
         [:div {:style {:transition "opacity .15s linear"}}
          [:div {:role "document"
                 :style {:justify-content "center"
                         :margin "1.75rem auto"}}
           [:div {:style (merge {:width "auto"} (:style modal))}
            [:div.modal-body {:style {:background-color "#fefefe"
                                      :margin "auto"
                                      :padding "20px"
                                      :border "1px solid #888"
                                      :width "52%"}}
             [:div {:style {:position "relative"}}
              [:h2 {:style {:font-size "24px"
                            :font-weight "900"}}
               (:title modal)]
              [:button {:type "button"
                        :style {:font-size "1.5rem"
                                :font-weight "700"
                                :opacity ".5"
                                :position "absolute"
                                :top "0"
                                :right "0"}
                        :on-click #(do (when-let [close (:close modal)] (close))
                                       (rf/dispatch [::modal/close-modal]))}
               [:span "×"]]]
             (:body modal)]
            (when (or (:accept modal) (:cancel modal))
              [:div.btn-component>div.col-sm.pb-4

               (when-let [accept (:accept modal)]
                 [:button.btn {:class (or (:class accept) "save")
                               :type "button"
                               :on-click #(do (when-let [accept-fn (:fn accept)] (accept-fn))
                                              (when-not (:validation modal)
                                                (rf/dispatch [:close-modal])))}
                  (:text accept)])
               (when-let [cancel (:cancel modal)]
                 [:button.btn {:class (or (:class cancel) "cancel") :type "button"
                               :on-click #(do (when-let [cancel-fn (:fn cancel)] (cancel-fn))
                                              (rf/dispatch [:close-modal]))}
                  (:text cancel)])])]]]]))))
