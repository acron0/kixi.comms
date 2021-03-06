(ns kixi.types
  (:require [clojure.spec.alpha :as s]
            [com.gfredericks.schpec :as sh]
            [clojure.spec.gen.alpha :as gen]

            [clj-time.core :as t]
            [clj-time.format :as tf]))

(defn -regex?
  [rs]
  (fn [x]
    (if (and (string? x) (re-find rs x))
      x
      ::s/invalid)))

(def uuid?
  (-regex? #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))

(def uuid
  (s/with-gen
    (s/conformer uuid?)
    #(gen/fmap str (gen/uuid))))


(def format :basic-date-time)

(def formatter
  (tf/formatters format))

(def time-parser
  (partial tf/parse formatter))

(defn timestamp?
  [x]
  (if (instance? org.joda.time.DateTime x)
    x
    (try
      (if (string? x)
        (time-parser x)
        ::s/invalid)
      (catch IllegalArgumentException e
        ::s/invalid))))

(def timestamp
  (s/with-gen
    (s/conformer timestamp?)
    #(gen/return (t/now))))
