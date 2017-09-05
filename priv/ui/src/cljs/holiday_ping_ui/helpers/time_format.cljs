(ns holiday-ping-ui.helpers.time-format
  (:require
   [cljs-time.core :as time]
   [cljs-time.format :as format]))

(defn string-to-date
  [s]
  (let [formatter (format/formatters :date)]
    (format/parse-local-date formatter s)))

(defn date-to-string
  [d]
  (let [formatter (format/formatters :date)]
    (format/unparse formatter d)))

(defn month-string
  [year month]
  (let [date      (time/local-date year month 1)
        formatter (format/formatter "MMMM yyyy")]
    (format/unparse formatter date)))