--liquibase formatted sql
--changeset contentaggregator:02-create-content-items splitStatements:false
CREATE TABLE IF NOT EXISTS content_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id VARCHAR(64) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(512) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    content_text TEXT,
    raw_data JSONB,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('russian', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('russian', coalesce(content_text, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content_text, '')), 'B')
    ) STORED,
    embedding halfvec(1536),
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_content_items_source_external UNIQUE (source_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_content_items_hnsw_embedding
    ON content_items USING hnsw (embedding halfvec_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_content_items_search_vector
    ON content_items USING gin (search_vector);

CREATE INDEX IF NOT EXISTS idx_content_items_unread_feed
    ON content_items (published_at DESC)
    INCLUDE (id, title, url, source_id)
    WHERE read = FALSE;

CREATE INDEX IF NOT EXISTS idx_content_items_source_pub
    ON content_items (source_id, published_at DESC);
