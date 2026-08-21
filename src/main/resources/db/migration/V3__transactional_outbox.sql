CREATE TABLE outbox_stream (
    topic VARCHAR(128) NOT NULL,
    partition_key VARCHAR(128) NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (topic, partition_key),
    CONSTRAINT ck_outbox_stream_sequence CHECK (last_sequence >= 0)
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    operation_key VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    partition_key VARCHAR(128) NOT NULL,
    schema_name VARCHAR(128) NOT NULL,
    deduplication_key VARCHAR(128) NOT NULL,
    stream_sequence BIGINT NOT NULL,
    payload BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    published_at TIMESTAMPTZ,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    lease_version BIGINT NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1024),
    CONSTRAINT fk_outbox_operation
        FOREIGN KEY (operation_key)
        REFERENCES wallet_operation(idempotency_key)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_outbox_stream
        FOREIGN KEY (topic, partition_key)
        REFERENCES outbox_stream(topic, partition_key),
    CONSTRAINT uq_outbox_semantic_event UNIQUE (topic, deduplication_key),
    CONSTRAINT uq_outbox_operation UNIQUE (operation_key),
    CONSTRAINT uq_outbox_stream_sequence UNIQUE (topic, partition_key, stream_sequence),
    CONSTRAINT ck_outbox_strings CHECK (
        btrim(operation_key) <> ''
        AND btrim(topic) <> ''
        AND btrim(partition_key) <> ''
        AND btrim(schema_name) <> ''
        AND btrim(deduplication_key) <> ''
    ),
    CONSTRAINT ck_outbox_payload CHECK (octet_length(payload) > 0),
    CONSTRAINT ck_outbox_stream_position CHECK (stream_sequence > 0),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_lease_version CHECK (lease_version >= 0),
    CONSTRAINT ck_outbox_lease_pair CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    ),
    CONSTRAINT ck_outbox_published_lease CHECK (
        published_at IS NULL OR (lease_owner IS NULL AND lease_until IS NULL)
    )
);

CREATE INDEX ix_outbox_claim_due
    ON outbox_event (available_at, stream_sequence)
    WHERE published_at IS NULL;

CREATE INDEX ix_outbox_fifo
    ON outbox_event (topic, partition_key, stream_sequence)
    WHERE published_at IS NULL;

CREATE INDEX ix_outbox_lease_expiry
    ON outbox_event (lease_until)
    WHERE published_at IS NULL AND lease_owner IS NOT NULL;
