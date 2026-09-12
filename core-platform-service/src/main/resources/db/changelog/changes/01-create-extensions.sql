--liquibase formatted sql
--changeset contentaggregator:01-create-extensions splitStatements:false
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
