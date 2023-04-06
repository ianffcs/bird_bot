(ns bird-bot.build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/delete {:path "target"})
    (b/write-pom {:class-dir class-dir
                  :lib       'bird-bot/bird-bot
                  :version   "1.0.0"
                  :basis     basis})
    #_(b/copy-dir {:src-dirs   (:paths basis)
                   :target-dir class-dir})
    (b/compile-clj {:basis     basis
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :main      'bird-bot.main
             :uber-file "target/bird-bot.jar"
             :basis     basis})))
