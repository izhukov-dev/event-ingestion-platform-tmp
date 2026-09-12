--liquibase formatted sql
--changeset contentaggregator:03-add-locked-until splitStatements:false
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending_lease
    ON outbox_events (created_at ASC)
    WHERE processed_at IS NULL;
