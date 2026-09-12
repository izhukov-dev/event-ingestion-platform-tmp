--liquibase formatted sql
--changeset contentaggregator:04-add-unique-aggregate-id splitStatements:false
CREATE UNIQUE INDEX IF NOT EXISTS uq_outbox_events_aggregate_id 
    ON outbox_events (aggregate_id);
