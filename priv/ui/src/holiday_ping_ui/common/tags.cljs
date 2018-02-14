(ns holiday-ping-ui.common.tags
  "Input component that populates a list of tags when a valid value is entered."
  (:require
   [clojure.string :as string]
   [reagent.core  :as reagent]
   [re-frame.core :as re-frame]))

(defn last-separator?
  [value]
  (and (not (string/blank? value))
       (or (string/blank? (last value))
           (= "," (last value)))))

(defn trim-separator
  [value]
  (if (last-separator? value)
    (apply str (butlast value))
    value))

(defn on-change
  "If a separator value (whitespace or comma) is entered, and the input is
   valid, push it to the state array, otherwise keep filling the input."
  [state input-state valid?]
  (fn [event]
    (let [value (-> event .-target .-value)]
      (if (and (last-separator? value) valid?)
        (do
          (swap! state conj (trim-separator value))
          (reset! input-state ""))
        (reset! input-state value)))))

(defn on-blur
  "If the input valie is valid, push it to the state array when focus moves out
  of the field."
  [state input-state valid?]
  (fn [event]
    (let [value (string/trim (-> event .-target .-value))]
      (when (and valid? (not (string/blank? value)))
        (swap! state conj value)
        (reset! input-state "")))))

(defn on-key-press
  "Attempt to push a valid value to the state array when enter is pressed."
  [state input-state valid?]
  (fn [event]
    (let [value  (-> event .-target .-value)
          enter? (= 13 (.-charCode event))]
      (when enter? (.preventDefault event))
      (if (and enter? valid?)
        (do
          (swap! state conj value)
          (reset! input-state ""))
        (reset! input-state value)))))

(defn delete-nth-tag
  [state n]
  (reset! state
          (keep-indexed #(if (not= %1 n) %2) @state)))

(defn validate-sub
  [state input-state validate-kw]
  (if validate-kw
    @(re-frame/subscribe [validate-kw (trim-separator @input-state) @state])
    [true]))

(defn input
  "Component that renders a text input and a list of tags whenever a valid
  item is entered."
  [state {:keys [validate help-text name label]}]
  (let [input-state (reagent/atom "")]
    (fn [state spec]
      (let [[valid? message] (validate-sub state input-state validate)
            valid?           (or valid? (string/blank? @input-state))]
        [:div
         [:div.field.is-grouped.is-grouped-multiline
          (for [[tag i] (zipmap (or @state []) (range))]
            [:div.control {:key tag}
             [:div.tags.has-addons
              [:span.tag tag]
              [:a.tag.is-delete
               {:on-click #(delete-nth-tag state i)}]]])]
         [:input.input {:type         "text"
                        :name         name
                        :label        (or label name)
                        :value        @input-state
                        :class        (when-not valid? "is-danger")
                        :on-change    (on-change state input-state valid?)
                        :on-blur      (on-blur state input-state valid?)
                        :on-key-press (on-key-press state input-state valid?)}]
         (when-not valid?
           [:p.help.is-danger message])
         (when help-text
           [:p.help help-text])]))))
