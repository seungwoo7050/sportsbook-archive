-- Persist each correction plan before contacting Wallet.

CREATE TABLE settlement_revision (
    revision_id             UUID                     PRIMARY KEY,
    bet_id                  UUID                     NOT NULL REFERENCES bet (bet_id),
    revision_number         BIGINT                   NOT NULL,
    user_id                 UUID                     NOT NULL,
    event_id                UUID                     NOT NULL,
    source_candidate_id     UUID                     NOT NULL REFERENCES result_candidate (candidate_id),
    previous_result         VARCHAR(8)               NOT NULL,
    new_result              VARCHAR(8)               NOT NULL,
    previous_payout_amount  BIGINT                   NOT NULL,
    new_payout_amount       BIGINT                   NOT NULL,
    currency                VARCHAR(3)               NOT NULL,
    slip_type               VARCHAR(16)              NOT NULL,
    system_min_wins         INT,
    system_total_selections INT,
    unit_stake_amount       BIGINT                   NOT NULL,
    source_result_settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state                   VARCHAR(16)              NOT NULL,
    lease_token             UUID,
    lease_until             TIMESTAMP WITH TIME ZONE,
    attempt_count           INT                      NOT NULL DEFAULT 0,
    next_retry_at           TIMESTAMP WITH TIME ZONE,
    last_error_code         VARCHAR(128),
    wallet_status           VARCHAR(16),
    wallet_queue_sequence   BIGINT,
    wallet_operation_group_id UUID,
    wallet_queued_at        TIMESTAMP WITH TIME ZONE,
    wallet_applied_at       TIMESTAMP WITH TIME ZONE,
    wallet_next_attempt_at  TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at              TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_settlement_revision_number UNIQUE (bet_id, revision_number),
    CONSTRAINT uq_settlement_revision_source UNIQUE (bet_id, source_candidate_id),
    CONSTRAINT ck_settlement_revision_number CHECK (revision_number >= 1),
    CONSTRAINT ck_settlement_revision_result CHECK (
        previous_result IN ('WON', 'LOST', 'PUSH', 'VOID')
        AND new_result IN ('WON', 'LOST', 'PUSH', 'VOID')),
    CONSTRAINT ck_settlement_revision_payout CHECK (
        previous_payout_amount >= 0 AND new_payout_amount >= 0),
    CONSTRAINT ck_settlement_revision_stake CHECK (unit_stake_amount > 0),
    CONSTRAINT ck_settlement_revision_slip CHECK (
        (slip_type IN ('SINGLE', 'MULTIPLE')
            AND system_min_wins IS NULL AND system_total_selections IS NULL)
        OR (slip_type = 'SYSTEM' AND system_min_wins BETWEEN 1 AND system_total_selections
            AND system_total_selections BETWEEN 1 AND 15)),
    CONSTRAINT ck_settlement_revision_state CHECK (
        state IN ('PENDING', 'BLOCKED', 'EXHAUSTED', 'APPLIED', 'REJECTED')),
    CONSTRAINT ck_settlement_revision_lease CHECK (
        (lease_token IS NULL AND lease_until IS NULL)
        OR (lease_token IS NOT NULL AND lease_until IS NOT NULL
            AND state IN ('PENDING', 'BLOCKED'))),
    CONSTRAINT ck_settlement_revision_applied CHECK (
        (state = 'APPLIED' AND applied_at IS NOT NULL)
        OR (state <> 'APPLIED' AND applied_at IS NULL)),
    CONSTRAINT ck_settlement_revision_blocked_proof CHECK (
        (wallet_status IS NOT NULL AND wallet_status = 'BLOCKED'
            AND state IN ('PENDING', 'BLOCKED')
            AND wallet_queue_sequence IS NOT NULL AND wallet_queued_at IS NOT NULL
            AND wallet_operation_group_id IS NULL AND wallet_applied_at IS NULL
            AND wallet_next_attempt_at IS NOT NULL)
        OR ((wallet_status IS NULL
                OR (wallet_status IS NOT NULL AND wallet_status <> 'BLOCKED'))
            AND wallet_next_attempt_at IS NULL)),
    CONSTRAINT ck_settlement_revision_wallet_state CHECK (
        wallet_status IS NULL
        OR (wallet_status IS NOT NULL AND wallet_status = 'BLOCKED'
            AND state IN ('PENDING', 'BLOCKED'))
        OR (wallet_status IS NOT NULL AND wallet_status = 'APPLIED' AND state = 'APPLIED')
        OR (wallet_status IS NOT NULL AND wallet_status = 'REJECTED' AND state = 'REJECTED')),
    CONSTRAINT ck_settlement_revision_applied_proof CHECK (
        (wallet_status IS NOT NULL AND wallet_status = 'APPLIED'
            AND wallet_operation_group_id IS NOT NULL AND wallet_applied_at IS NOT NULL
            AND wallet_next_attempt_at IS NULL)
        OR ((wallet_status IS NULL
                OR (wallet_status IS NOT NULL AND wallet_status <> 'APPLIED'))
            AND wallet_operation_group_id IS NULL AND wallet_applied_at IS NULL)),
    CONSTRAINT ck_settlement_revision_rejected_proof CHECK (
        (wallet_status IS NOT NULL AND wallet_status = 'REJECTED'
            AND wallet_queue_sequence IS NULL AND wallet_operation_group_id IS NULL
            AND wallet_queued_at IS NULL AND wallet_applied_at IS NULL
            AND wallet_next_attempt_at IS NULL)
        OR (wallet_status IS NULL
            OR (wallet_status IS NOT NULL AND wallet_status <> 'REJECTED'))),
    CONSTRAINT ck_settlement_revision_queue_identity CHECK (
        (wallet_queue_sequence IS NULL AND wallet_queued_at IS NULL)
        OR (wallet_queue_sequence IS NOT NULL AND wallet_queued_at IS NOT NULL
            AND wallet_queue_sequence > 0 AND new_payout_amount < previous_payout_amount)),
    CONSTRAINT ck_settlement_revision_attempts CHECK (attempt_count BETWEEN 0 AND 12),
    CONSTRAINT ck_settlement_revision_schedule CHECK (
        (lease_token IS NOT NULL AND next_retry_at IS NULL)
        OR (lease_token IS NULL AND state IN ('PENDING', 'BLOCKED')
            AND attempt_count < 12 AND next_retry_at IS NOT NULL)
        OR (lease_token IS NULL AND state = 'BLOCKED' AND next_retry_at IS NULL
            AND wallet_status IS NOT NULL AND wallet_status = 'BLOCKED'
            AND last_error_code IS NOT NULL)
        OR (lease_token IS NULL AND state IN ('EXHAUSTED', 'APPLIED', 'REJECTED')
            AND next_retry_at IS NULL)),
    CONSTRAINT ck_settlement_revision_exhausted CHECK (
        state <> 'EXHAUSTED'
        OR (last_error_code IS NOT NULL AND wallet_status IS NULL
            AND wallet_queue_sequence IS NULL AND wallet_operation_group_id IS NULL
            AND wallet_queued_at IS NULL AND wallet_applied_at IS NULL
            AND wallet_next_attempt_at IS NULL)),
    CONSTRAINT ck_settlement_revision_rejected_state CHECK (
        state <> 'REJECTED'
        OR (wallet_status IS NOT NULL AND wallet_status = 'REJECTED')
        OR (wallet_status IS NULL AND last_error_code IS NOT NULL))
);

CREATE INDEX ix_settlement_revision_recovery
    ON settlement_revision (state, next_retry_at, wallet_next_attempt_at, revision_id);

CREATE TABLE settlement_revision_selection (
    revision_id UUID       NOT NULL REFERENCES settlement_revision (revision_id) ON DELETE CASCADE,
    selection_id UUID      NOT NULL,
    leg_index    INT       NOT NULL,
    odds         NUMERIC(9,4) NOT NULL,
    outcome      VARCHAR(8) NOT NULL,
    CONSTRAINT pk_settlement_revision_selection PRIMARY KEY (revision_id, selection_id),
    CONSTRAINT uq_settlement_revision_selection_order UNIQUE (revision_id, leg_index),
    CONSTRAINT ck_settlement_revision_selection_index CHECK (leg_index >= 0),
    CONSTRAINT ck_settlement_revision_selection_odds CHECK (odds >= 1),
    CONSTRAINT ck_settlement_revision_selection_outcome CHECK (
        outcome IN ('WON', 'LOST', 'PUSH', 'VOID'))
);
