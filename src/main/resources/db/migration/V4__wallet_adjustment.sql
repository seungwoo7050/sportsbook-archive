CREATE TABLE wallet_adjustment (
    revision_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    bet_id UUID NOT NULL,
    revision_number BIGINT NOT NULL,
    user_id UUID NOT NULL,
    previous_payout_amount BIGINT NOT NULL,
    new_payout_amount BIGINT NOT NULL,
    delta_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    queue_sequence BIGINT,
    operation_group_id UUID UNIQUE,
    queued_at TIMESTAMPTZ,
    applied_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wallet_adjustment_bet_revision
        UNIQUE (bet_id, revision_number),
    CONSTRAINT uq_wallet_adjustment_user_sequence
        UNIQUE (user_id, queue_sequence),
    CONSTRAINT fk_wallet_adjustment_operation
        FOREIGN KEY (idempotency_key)
        REFERENCES wallet_operation(idempotency_key)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_wallet_adjustment_operation_group
        FOREIGN KEY (idempotency_key, operation_group_id)
        REFERENCES wallet_operation(idempotency_key, operation_group_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_wallet_adjustment_request CHECK (
        revision_number >= 1
        AND idempotency_key = 'settlement:revision:' || revision_id::text
        AND user_id NOT IN (
            '00000000-0000-7000-8000-000000000001',
            '00000000-0000-7000-8000-000000000002'
        )
        AND previous_payout_amount >= 0
        AND new_payout_amount >= 0
        AND delta_amount <> 0
        AND delta_amount = new_payout_amount - previous_payout_amount
        AND currency IN ('KRW', 'USD')
    ),
    CONSTRAINT ck_wallet_adjustment_status CHECK (
        status IN ('APPLIED', 'BLOCKED', 'REJECTED')
    ),
    CONSTRAINT ck_wallet_adjustment_queue_pair CHECK (
        (queue_sequence IS NULL AND queued_at IS NULL)
        OR (queue_sequence > 0 AND queued_at IS NOT NULL)
    ),
    CONSTRAINT ck_wallet_adjustment_outcome CHECK (
        (
            status = 'APPLIED'
            AND operation_group_id IS NOT NULL
            AND applied_at IS NOT NULL
            AND next_attempt_at IS NULL
            AND (queue_sequence IS NULL OR delta_amount < 0)
            AND (queue_sequence IS NOT NULL OR retry_count = 0)
        )
        OR (
            status = 'BLOCKED'
            AND operation_group_id IS NULL
            AND queue_sequence IS NOT NULL
            AND next_attempt_at IS NOT NULL
            AND applied_at IS NULL
            AND delta_amount < 0
        )
        OR (
            status = 'REJECTED'
            AND operation_group_id IS NULL
            AND queue_sequence IS NULL
            AND applied_at IS NULL
            AND next_attempt_at IS NULL
            AND retry_count = 0
        )
    ),
    CONSTRAINT ck_wallet_adjustment_retry CHECK (retry_count >= 0)
);

CREATE INDEX ix_wallet_adjustment_fifo
    ON wallet_adjustment (user_id, queue_sequence)
    WHERE status = 'BLOCKED';

CREATE INDEX ix_wallet_adjustment_due
    ON wallet_adjustment (next_attempt_at, user_id)
    WHERE status = 'BLOCKED';
