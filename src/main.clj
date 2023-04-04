(ns main
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.java.io :as jio]
            [clojure.set :as set]
            [clojure.edn :as edn])
  (:import (java.io File Reader Writer)
           (java.net.http HttpClient
                          HttpRequest
                          HttpRequest$BodyPublishers
                          HttpResponse$BodyHandlers)
           (java.net URI)
           (java.util Properties)))


(defn- assoc-some-transient! [m k v]
  (if (nil? v) m (assoc! m k v)))


(defn assoc-some
  "Associates a key k, with a value v in a map m, if and only if v is not nil."
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (loop [acc (assoc-some-transient! (transient (or m {})) k v)
          kvs kvs]
     (if (next kvs)
       (recur (assoc-some-transient! acc (first kvs) (second kvs)) (nnext kvs))
       (if (zero? (count acc))
         m
         (persistent! acc))))))


(defn load-props
  [file-name]
  (with-open [^Reader reader (clojure.java.io/reader file-name)]
    (let [props (Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (read-string v)])))))

(defonce sys
  (merge
    (load-props "sys.properties")
    {:chat-id      -1001146934165
     ;;;"message_thread_id"
     :chat-rooms   {:programar 29
                    :musica    278
                    #_#_:receitas ""
                    #_#_:filosofias-politica ""
                    #_#_:shitpost ""
                    #_#_:plano-real ""}
     :backup-path  "my-data.edn"
     :telegram-log (atom #{})}))

(defn build-dict [words]
  (reduce (fn [dict, [current-word next-word]]
            (update-in dict [current-word] conj next-word))
          {} (partition 2 1 words)))


(defn get-bookend-words [sentences which-end]
  (mapv (fn [sentence]
          (which-end (str/split sentence #" "))) sentences))


(defn markov-generate-dict [text]
  (let [sentences (str/split text #"\. ")                   ; TODO: handle '?'
        words (mapcat #(str/split %1 #" ") sentences)]
    {:first-words (get-bookend-words sentences first)
     :last-words  (set (get-bookend-words sentences last))
     :dict        (build-dict words)}))


(defn recursively-construct-sentence [dict words]
  (let [current-word (last words)]

    (if ((:last-words dict) current-word)
      words
      (->> (get (:dict dict) current-word)
           rand-nth
           (conj words)
           (recursively-construct-sentence dict)))))


(defn generate-sentence [dict]
  (let [first-word (rand-nth (:first-words dict))]
    (str/join " " (recursively-construct-sentence dict [first-word]))))


(defn random-generated-text
  [telegram-log]
  (->> telegram-log
       (sort-by :update_id)
       (map #(-> % (get-in [:message :text])))
       (str/join "\n")
       markov-generate-dict
       (partial generate-sentence)
       repeatedly
       (take 100)
       rand-nth))


(defn telegram-fetcher-data!
  [{:keys [telegram-log]}]
  (let [offset-num (some->> @telegram-log
                            (sort-by :update_id)
                            last
                            :update_id
                            inc)
        resp (.send (HttpClient/newHttpClient)
                    (-> (str "https:"
                             "//api.telegram.org"
                             "/bot"
                             (:token sys)
                             "/getUpdates"
                             (when offset-num
                               (str "?offset=" offset-num)))
                        (URI/create)
                        (HttpRequest/newBuilder)
                        (.build))
                    (HttpResponse$BodyHandlers/ofString))
        tg-data (when (= 200 (.statusCode resp))
                  (->> (json/read-str (.body resp)
                                      :key-fn keyword)
                       :result
                       (into #{})))]
    tg-data))

(defn telegram-sender-data!
  [{:keys [telegram-log
           chat-id
           token]}]
  (let [rand-room (-> sys
                      :chat-rooms
                      vals
                      (conj nil)
                      rand-nth)
        request-map (assoc-some {"chat_id" chat-id
                                 "text"    (random-generated-text @telegram-log)}
                                "rand-room"
                                rand-room)
        resp (.send (HttpClient/newHttpClient)
                    (-> (str "https://api.telegram.org/"
                             "bot"
                             token
                             "/sendMessage")
                        (URI/create)
                        (HttpRequest/newBuilder)
                        (.header "Content-Type" "application/json")
                        (.POST (->> request-map
                                    json/write-str
                                    (HttpRequest$BodyPublishers/ofString)))
                        (.build))
                    (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp)
     :body   (json/read-str (.body resp)
                            :key-fn keyword)}))

(defn dump-local-data!
  [{:keys [backup-path
           telegram-log]}]
  (with-open [^Writer w (jio/writer backup-path)]
    (.write w (str (->> @telegram-log
                        (remove #(-> %
                                     :update_id
                                     (= 624742737)))
                        vec)))))

#_(telegram-fetcher-data! sys)
#_(dump-local-data! sys)
#_(->> (telegram-fetcher-data! sys)
       (sort-by :update_id))

(defn read-backup-data! [sys]
  (let [{:keys [backup-path
                telegram-log]} sys]
    (if (.exists (File. "my-data.edn"))
      (into #{}
            (edn/read-string (slurp backup-path)))
      #{})))

(defn -main
  [& {:keys [dont-send]}]
  (->> (telegram-fetcher-data! sys)
       (swap! (:telegram-log sys)
              set/union
              (read-backup-data! sys)))
  (when-not dont-send
    (telegram-sender-data! sys))
  (dump-local-data! sys)
  (-> sys :telegram-log deref count println))

#_(printf (str/join #"\n"
                    (repeatedly 5 (fn []
                                    (-> "zap-chat-file.txt"
                                        slurp
                                        (str/split-lines)
                                        (->> (map #(str/split % #":" 4))
                                             (filter #(some-> %
                                                              (get 2)
                                                              (str/includes? "Nome")))
                                             (map #(some-> %
                                                           (get 3)
                                                           str/trim))
                                             (remove #(some-> %
                                                              (str/includes? "\u200E")))
                                             (remove nil?)
                                             (str/join "\n")
                                             markov-generate-dict
                                             (partial generate-sentence)
                                             repeatedly
                                             (take 100)
                                             rand-nth))))))