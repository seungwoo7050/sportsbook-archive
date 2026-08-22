-- Wallet Kafka events are durable wake-up hints. HTTP remains authoritative.
ALTER TABLE bet
    ADD COLUMN reconciliation_requested_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE wallet_event_receipt (
    event_id        UUID                     PRIMARY KEY,
    topic           VARCHAR(64)              NOT NULL,
    bet_id          UUID                     NOT NULL REFERENCES bet (bet_id),
    user_id         UUID                     NOT NULL,
    payload_sha256  VARCHAR(64)              NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT wallet_event_topic_valid CHECK (
        topic IN ('wallet.debited.v1', 'wallet.debit-failed.v1')
    ),
    CONSTRAINT wallet_event_payload_hash_valid CHECK (
        payload_sha256 ~ '^[0-9a-f]{64}$'
    )
);

COMMENT ON TABLE wallet_event_receipt IS
    'Deduplicates at-least-once wallet wake-up events by their event-id header.';
COMMENT ON COLUMN wallet_event_receipt.payload_sha256 IS
    'Detects a conflicting payload replay under the same event-id.';
COMMENT ON COLUMN bet.reconciliation_requested_at IS
    'Latest durable request to confirm placement through authoritative HTTP state.';

CREATE INDEX ix_wallet_event_pending
    ON wallet_event_receipt (received_at)
    WHERE processed_at IS NULL;
