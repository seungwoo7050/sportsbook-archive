-- Durable observations preserve causal evidence; tombstones permanently latch terminal events.

CREATE TABLE event_lifecycle_observation (
    observation_id    UUID                     PRIMARY KEY,
    event_id           UUID                     NOT NULL,
    status             VARCHAR(16)              NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_start_at TIMESTAMP WITH TIME ZONE,
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    fingerprint        CHAR(64)                 NOT NULL,
    CONSTRAINT uq_event_lifecycle_fingerprint UNIQUE (event_id, fingerprint),
    CONSTRAINT ck_event_lifecycle_status CHECK (
        status IN ('SCHEDULED', 'IN_PLAY', 'FINISHED', 'CANCELLED', 'POSTPONED'))
);

CREATE INDEX ix_event_lifecycle_order
    ON event_lifecycle_observation (event_id, occurred_at, fingerprint);

CREATE TABLE event_lifecycle_tombstone (
    event_id        UUID                     PRIMARY KEY,
    terminal_status VARCHAR(16)              NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    fingerprint     CHAR(64)                 NOT NULL,
    CONSTRAINT ck_event_lifecycle_terminal CHECK (
        terminal_status IN ('CANCELLED', 'POSTPONED'))
);

COMMENT ON TABLE event_lifecycle_tombstone IS
    'Non-expiring first terminal latch; late nonterminal observations cannot revive an event.';
