-- Application document registry (SRS 4.1). The vector_store table is managed by Spring AI.
CREATE TABLE IF NOT EXISTS app_user (
    user_id       UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS document_registry (
    document_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       VARCHAR(128) NOT NULL,
    file_name     VARCHAR(512) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    file_size     BIGINT       NOT NULL,
    sha256_hash   CHAR(64)     NOT NULL,
    chunk_count   INTEGER      DEFAULT 0,
    page_count    INTEGER      DEFAULT 0,
    status        VARCHAR(32)  NOT NULL CHECK (status IN ('PROCESSING', 'INDEXED', 'FAILED')),
    error_message TEXT,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_doc_registry_user ON document_registry(user_id);
CREATE INDEX IF NOT EXISTS idx_doc_registry_hash ON document_registry(user_id, sha256_hash);
