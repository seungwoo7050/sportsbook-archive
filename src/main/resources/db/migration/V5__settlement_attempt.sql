-- V5: durable PENDING-only settlement claim.
--
-- A wallet operation is external to the database transaction. Persisting the complete money
-- plan before the first call gives retries and crash recovery one immutable source of truth.

CREATE TABLE settlement_attempt (
    bet_id                 UUID                     PRIMARY KEY REFERENCES bet (bet_id),
    action                 VARCHAR(8)               NOT NULL,
    event_id               UUID                     NOT NULL,
    result                 VARCHAR(8),
    void_reason            VARCHAR(32),
    committed_amount       BIGINT                   NOT NULL,
    payout_amount          BIGINT                   NOT NULL,
    locked_release_amount  BIGINT                   NOT NULL,
    locked_forfeit_amount  BIGINT                   NOT NULL,
    house_profit_amount    BIGINT                   NOT NULL,
    currency               VARCHAR(3)               NOT NULL,
    lease_token            UUID,
    lease_until            TIMESTAMP WITH TIME ZONE,
    attempt_count          INT                      NOT NULL,
    last_error             VARCHAR(1000),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_settlement_attempt_action
        CHECK ((action = 'SETTLE' AND result IS NOT NULL AND void_reason IS NULL)
            OR (action = 'VOID' AND result IS NULL AND void_reason IS NOT NULL)),
    CONSTRAINT ck_settlement_attempt_amounts_nonnegative
        CHECK (committed_amount >= 0 AND payout_amount >= 0
            AND locked_release_amount >= 0 AND locked_forfeit_amount >= 0
            AND house_profit_amount >= 0),
    CONSTRAINT ck_settlement_attempt_committed_conservation
        CHECK (locked_release_amount + locked_forfeit_amount = committed_amount),
    CONSTRAINT ck_settlement_attempt_payout_conservation
        CHECK (locked_release_amount + house_profit_amount = payout_amount),
    CONSTRAINT ck_settlement_attempt_lease_pair
        CHECK ((lease_token IS NULL AND lease_until IS NULL)
            OR (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CONSTRAINT ck_settlement_attempt_count CHECK (attempt_count >= 1)
);

COMMENT ON TABLE settlement_attempt IS
    'Durable first-action settlement claim; one immutable wallet plan per PENDING bet.';

CREATE INDEX ix_settlement_attempt_recovery
    ON settlement_attempt (lease_until, updated_at);
