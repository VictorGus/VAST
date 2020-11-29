(ns app.filters
  (:require [app.dbcore :as db]
            [clojure.set :as set]
            [clojure.string :as str]))


(def global-keys
  {"none" :none
   "some" :some})


(def tokens-definitions
  {"!" {:key :status}
   "@" {:key  :assignee
        :local-keys {"me" :me}}
   "#" {:key :tag}
   "~" {:key        :date
        :local-keys {"today" (fn [] (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (new java.util.Date)))}}})

(def redirect-token-def
  {"date" (get tokens-definitions "~")
   "assign" (get tokens-definitions "@")})

(defn find-token-def [token]
  (when (> (count token) 1)
    (let [magic-char (subs token 0 1)
          t          (subs token 1)
          token-def  (get tokens-definitions magic-char)
          keys       (merge global-keys (:local-keys token-def))
          value      (get keys t t)]
      {(:key token-def) (if-let [gk (get global-keys t)]
                          {gk true}
                          {:values #{(if (fn? value)
                                       (value)
                                       value)}})})))


(type (fn [] 42))

(defn parse-token [token]
  (cond
    (str/includes? token ":")
    (let [[k v] (str/split token #":" 2)
          kk (if (str/starts-with? k "-") (subs k 1) k)
          local-keys (get-in redirect-token-def [kk :local-keys] {})]
      (when (not (str/blank? v))
        (if (str/includes? v "~")
          (let [has-minus? (str/starts-with? k "-")
                outer-keyword (keyword (if has-minus? (subs k 1) k))
                inner-keyword (keyword (if has-minus? "not-in-range" "range"))]
            {outer-keyword {inner-keyword (mapv (fn [val]
                                                  (let [local-val (get local-keys val val)]
                                                    (if (fn? local-val)
                                                      (local-val)
                                                      local-val)))
                                                (str/split v #"~" 2))}})
          {(keyword k) (->> (reduce (fn [acc v]
                                      (let [has-minus? (str/starts-with? v "-")
                                            val (if has-minus? (subs v 1) v)
                                            gk (get global-keys val)
                                            val (get local-keys val val)]
                                        (if-not (nil? gk)
                                          (assoc acc gk true)
                                          (update acc (if has-minus? :not :values) #(conj % (if (fn? val)
                                                                                              (val)
                                                                                              val))))))
                                    {:not #{} :values #{}}
                                    (str/split v #","))
                            (filter (fn [[_ v]] (if (coll? v) (not-empty v) v)))
                            (into {}))})))
    (str/starts-with? token "-")
    (when (> (count token) 2) (let [token-def (find-token-def (subs token 1)) ;;naming?
                                    key (first (keys token-def))] ;;naming?
                                (update token-def key #(set/rename-keys % {:values :not}))))
    :else
    (find-token-def token)))

(defn parse-merge [map1 map2]
  (reduce (fn [acc [k v]]
            (if-let [v' (get acc k)]
              (assoc acc k (cond-> v'
                             (:not v)
                             (assoc :not (if (:not v')
                                           (into (:not v') (:not v))
                                           (:not v)))
                             (:values v)
                             (assoc :values (if (:values v')
                                              (into (:values v') (:values v))
                                              (:values v)))))
              (assoc acc k v)))
          map1
          map2))

(defn parse-filter [s]
  (when s
    (let [[filt text] (str/split s #"\|" 2)
          text (when (and text (not (str/blank? text))) (str/trim text))]
      (->> (str/split (str/trim (or filt "")) #"\s+")
           (reduce (fn [acc token] (parse-merge acc (parse-token token)))
                   (cond-> {}
                     text (assoc :text {:values text})))))))

