create table if not exists meteo_data (id text primary key not null, date_ts timestamp, direction text, speed text, elevation text);

create table if not exists sensor_data (id text primary key not null, date_ts timestamp, monitor text, reading text, chemical text);

create table if not exists monitor (id text primary key not null, longitude text, latitude text);

create table if not exists factory (id text primary key not null, longitude text, latitude text, factory_name text, description text);

SELECT 'CREATE DATABASE fortest TEMPLATE mobile-db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fortest')\gexec
