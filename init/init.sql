create table if not exists meteo_data (id text primary key not null, date_ts text, direction text, speed text, elevation text);

SELECT 'CREATE DATABASE fortest TEMPLATE mobile-db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fortest')\gexec
