(ns nexus.app.views
  (:require [reagent.core :as r]))


(defn header []
  [:header {:style {:background-color "#2c3e50"
                    :color "white"
                    :padding "1rem"
                    :margin-bottom "2rem"}}
   [:h1 "Nexus - ClojureScript Frontend!"]])

(defonce count-atom (r/atom 0))

(comment
  (reset! count-atom nil)
  (js/alert "fala meu amor, perfect, linda, princesa")

  (= "foo" "foo")
  (= {:prop1 "yes"} {:prop1 "yes"})
  (= {:prop1 "yes" :obj {:prop2 "no"}} {:prop1 "yes"})
  (= {:prop1 "yes" :obj {:prop2 "no"}} {:prop1 "yes" :obj {:prop2 "no"}})
  (= {:prop1 "yes" :obj {:prop2 "no"}} {:prop1 "yes" :obj {:prop2 "no" :prop3 "yes?"}})

  (js/console.log (= {:prop1 "yes" :obj {:prop2 "no"}} {:prop1 "yes" :obj {:prop2 "no"}}))
  (js/console.log {:prop1 "yes" :obj {:prop2 "no"}})

  (= {:a "b"} {:a "b"})

  (= (clj->js {:a "b"}) (clj->js {:a "b"}))

  (js/console.log (clj->js {:hi "bro"}))

  (js/console.log (clj->js {:hi/thre.bro "bro"
                            :op/thre.bro "bro updated"
                            :third/thre.bro "bro three"}))

  (js/console.log (-> 0
                      (+ 2)
                      (+ 2)
                      (- 1)
                      ((fn [a b]
                         (/ a b)) 2)))

  ;
  )


(defn counter []
  (fn []
    [:div {:style {:margin "2rem 0"}}
     [:p "Counter: " (js/Number @count-atom)]
     [:button {:on-click #(swap! count-atom inc)
               :style {:margin-right "0.5rem"
                       :padding "0.5rem 1rem"
                       :cursor "pointer"}}
      "Increment"]
     [:button {:on-click #(swap! count-atom dec)
               :style {:padding "0.5rem 1rem"
                       :cursor "pointer"}}
      "Decrement"]]))

(defn api-test []
  (let [response (r/atom nil)
        loading? (r/atom false)]
    (fn []
      [:div {:style {:margin "2rem 0"}}
       [:h3 "API Test"]
       [:button {:on-click (fn []
                             (reset! loading? true)
                             (-> (js/fetch "/api/health")
                                 (.then #(.text %))
                                 (.then (fn [text]
                                          (reset! response text)
                                          (reset! loading? false)))
                                 (.catch (fn [error]
                                           (reset! response (str "Error: " error))
                                           (reset! loading? false)))))
                 :style {:padding "0.5rem 1rem"
                         :cursor "pointer"
                         :margin-bottom "1rem"}}
        "Test /api/health"]
       (when @loading?
         [:p "Loading..."])
       (when @response
         [:div {:style {:background-color "#ecf0f1"
                        :padding "1rem"
                        :border-radius "4px"}}
          [:strong "Response: "]
          [:span @response]])])))

(defonce todos-atom (r/atom [{:id 1
                              :title "First Todo"
                              :completed false}]))

(defn todo-list []
  (let [new-todo (r/atom "")]
    (fn []
      [:div {:class ["bg-blue-100 p-3 border border-red-300 rounded"]}
       [:h1 {:class ["text-3xl"]} "Hello!"]
       [:ul {:class "flex flex-col gap-2"}
        (for [todo @todos-atom]
          [:li {:key (:id todo)
                :style {:display "flex"
                        :flex-direction "column"}}
           [:span {} "Title: " (:title todo)]
           [:div {:class "flex"}
            [:button {:class "bg-sky-200 rounded inline-flex px-2 py-1"
                      :on-click
                      (fn []
                        (swap! todos-atom (fn [current]
                                            (for [item current]
                                              (if (= (:id todo) (:id item))
                                                (merge item {:completed (not (:completed todo))})
                                                item)))))}

             "Completed?" (if (:completed todo) "Yes!" "No.")]]])]

       [:form {:class "flex flex-col w-full bg-purple-100"
               :on-submit (fn [event]
                            (.preventDefault event)
                            (swap! todos-atom #(-> % (conj {:id (.getTime (js/Date.))
                                                            :title @new-todo
                                                            :completed false})))
                            (reset! new-todo ""))}
        [:span {} "Current value: " @new-todo]
        [:input {:class "border rouned shadow inline-flex w-full"
                 :placeholder "Type something here"
                 :value @new-todo
                 :on-change (fn [event]
                              (reset! new-todo (-> event .-target .-value)))}]]])))

(defn app []
  [:div {:style {:max-width "800px"
                 :margin "0 auto"
                 :font-family "sans-serif"}}
   [header]
   [:main {:style {:padding "0 1rem"}}
    [:p "Welcome to your ClojureScript + Reagent frontend!"]
    [counter]
    [api-test]
    [todo-list]]])

(comment
  (.getTime (js/Date.))

  (.toString (js/Date.))
  (* 20 3)
  (* 20 3)

  ;
  )
