(ns holiday-ping-ui.channels.events
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [holiday-ping-ui.common.events :as events]))

(defmethod events/load-view
  :channel-list
  [{:keys [db]} _]
  {:http-xhrio {:method          :get
                :uri             "/api/channels_detail"
                :timeout         8000
                :headers         {:authorization (str "Bearer " (:access-token db))}
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [:channel-list-success]
                :on-failure      [:channel-list-error]}})

(defmethod events/load-view
  :channel-edit
  [{:keys [db]} [_ channel-name]]
  {:http-xhrio {:method          :get
                :uri             (str "/api/channels/" channel-name)
                :timeout         8000
                :headers         {:authorization (str "Bearer " (:access-token db))}
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [:channel-detail-success]
                :on-failure      [:switch-view :not-found]}})

;; FIXME handling 401 here because it's the landing, but it would make more
;; sense to have a generic error handler that redirects to login from any
;; point that gets a 401
(re-frame/reg-event-fx
 :channel-list-error
 (fn [_ [_ {:keys [status]}]]
   (if (= 401 status)
     {:dispatch [:logout]}
     {:dispatch [:error-message "Channel loading failed."]})))

(re-frame/reg-event-db
 :channel-list-success
 (fn [db [_ response]]
   (-> db
       (assoc :channels response)
       (assoc :loading-view? false))))

(re-frame/reg-event-db
 :channel-detail-success
 (fn [db [_ response]]
   (-> db
       (assoc :channel-to-edit response)
       (assoc :loading-view? false))))

(re-frame/reg-event-fx
 :channel-delete
 (fn [{db :db} [_ channel]]
   {:http-xhrio {:method          :delete
                 :uri             (str "/api/channels/" channel)
                 :timeout         8000
                 :headers         {:authorization (str "Bearer " (:access-token db))}
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:navigate :channel-list]
                 :on-failure      [:error-message "Channel deleting failed."]}}))

(defn- clean-emoji
  [emoji]
  (when-not (string/blank? emoji)
    (let [emoji (string/trim emoji)]
      (str (when-not (= (first emoji) ":") ":")
           emoji
           (when-not (= (last emoji) ":") ":")))))

(defn- clean-slack-channel
  [channel]
  (let [channel (string/trim channel)]
    (str (when-not (= (first channel) "#") "#")
         channel)))

(defn- clean-slack-user
  [user]
  (let [user (string/trim user)]
    (str (when-not (= (first user) "@") "@")
         user)))

(defmulti clean-config
  (fn [type data] type))

(defmethod clean-config "slack"
  [_ {:keys [url channels users username emoji]}]
  (let [channels (map clean-slack-channel channels)
        users    (map clean-slack-user users)]
    {:channels (concat channels users)
     :url      url
     :username username
     :emoji    (clean-emoji emoji)}))

(defmethod clean-config "webhook"
  [_ {:keys [url secret]}]
  {:url    url
   :secret secret})

(defmethod clean-config :default
  [_ config] config)

(defn reminder-days-before
  "Translate the current form fields into the days before array expected by the
   backend."
  [{:keys [same-day days-before]}]
  (let [days-before (js/parseInt days-before)]
    (concat (if same-day [0] [])
            (if (zero? days-before) [] [days-before]))))

(re-frame/reg-event-fx
 :channel-edit-submit
 (fn [{db :db} [_ {:keys [name type time timezone] :as data}]]
   (let [params {:name                 name
                 :type                 type
                 :reminder_days_before (reminder-days-before data)
                 :reminder_time        time
                 :reminder_timezone    timezone
                 :configuration        (clean-config type data)}]
     {:http-xhrio {:method          :put
                   :uri             (str "/api/channels/" name)
                   :headers         {:authorization (str "Bearer " (:access-token db))}
                   :timeout         8000
                   :format          (ajax/json-request-format)
                   :params          params
                   :response-format (ajax/text-response-format)
                   :on-success      [:navigate :channel-list]
                   :on-failure      [:error-message "Channel submission failed"]}})))

(re-frame/reg-event-fx
 :wizard-submit
 (fn [{db :db} [_ {:keys [type channel-config reminder-config]}]]
   (let [channel-name (:name channel-config)
         params       {:name                 channel-name
                       :type                 type
                       :reminder_days_before (reminder-days-before reminder-config)
                       :reminder_time        (:time reminder-config)
                       :reminder_timezone    (:timezone reminder-config)
                       :configuration        (clean-config type channel-config)}]
     {:db         (assoc db :loading-view? true)
      :http-xhrio {:method          :put
                   :uri             (str "/api/channels/" channel-name)
                   :headers         {:authorization (str "Bearer " (:access-token db))}
                   :timeout         8000
                   :format          (ajax/json-request-format)
                   :params          params
                   :response-format (ajax/text-response-format)
                   :on-success      [:wizard-submit-success channel-name]
                   :on-failure      [:error-message "Channel submission failed"]}})))

(re-frame/reg-event-fx
 :wizard-submit-success
 (fn [_ [_ channel-name]]
   {:dispatch-n [[:holidays-save channel-name]
                 [:navigate :channel-list]]}))

(re-frame/reg-event-db
 :channel-test-start
 (fn [db [_ channel]]
   (assoc db :channel-to-test channel)))

(re-frame/reg-event-db
 :channel-test-cancel
 (fn [db _]
   (dissoc db :channel-to-test)))

(re-frame/reg-event-fx
 :channel-test-confirm
 (fn [{:keys [db]} [_ channel]]
   {:dispatch   [:channel-test-cancel]
    :http-xhrio {:method          :post
                 :uri             (str "/api/channels/" (:name channel) "/test")
                 :timeout         8000
                 :headers         {:authorization (str "Bearer " (:access-token db))}
                 :format          (ajax/json-request-format)
                 :params          {}
                 :response-format (ajax/text-response-format)
                 :on-success      [:success-message "Channel reminder sent."]
                 :on-failure      [:error-message "There was an error sending the reminder."]}}))
