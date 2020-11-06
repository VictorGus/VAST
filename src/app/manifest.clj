(ns app.manifest)

(def app-config
  {:db {:host     (or (System/getenv "PGHOST")     "localhost")
        :port     (or (System/getenv "PGPORT")     5443)
        :user     (or (System/getenv "PGUSER")     "postgres")
        :password (or (System/getenv "PGPASSWORD") "postgres")
        :dbname   (or (System/getenv "PGDATABASE") "vastbase")}
   :app {:port        (or (System/getenv "APP_PORT") 9090)
         :working-dir (or (System/getenv "WORKING_DIR") "/home/victor/Documents/Trash")}})

