CREATE TABLE wallet_operation (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    caller_id VARCHAR(16) NOT NULL,
    operation_kind VARCHAR(32) NOT NULL,
    user_id UUID NOT NULL,
    request_amount BIGINT NOT NULL,
    request_currency VARCHAR(3) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    operation_group_id UUID UNIQUE,
    failure_code VARCHAR(32),
    failure_http_status SMALLINT,
    failure_title VARCHAR(128),
    failure_detail VARCHAR(1024),
    failure_balance_amount BIGINT,
    failure_balance_currency VARCHAR(3),
    failure_expected_currency VARCHAR(3),
    requested_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_wallet_operation_caller CHECK (
        caller_id IN ('PLATFORM', 'BETTING', 'SETTLEMENT', 'ADMIN')
    ),
    CONSTRAINT ck_wallet_operation_kind CHECK (
        operation_kind IN (
            'DEPOSIT', 'WITHDRAW', 'BET_DEBIT', 'BET_PAYOUT',
            'BET_REFUND', 'BET_FORFEIT', 'BET_ADJUSTMENT'
        )
    ),
    CONSTRAINT ck_wallet_operation_caller_kind CHECK (
        (caller_id = 'PLATFORM' AND operation_kind IN ('DEPOSIT', 'WITHDRAW'))
        OR (caller_id = 'BETTING' AND operation_kind IN ('BET_DEBIT', 'BET_REFUND'))
        OR (caller_id = 'SETTLEMENT' AND operation_kind IN (
            'BET_PAYOUT', 'BET_REFUND', 'BET_FORFEIT', 'BET_ADJUSTMENT'
        ))
        OR (caller_id = 'ADMIN' AND operation_kind = 'BET_REFUND')
    ),
    CONSTRAINT ck_wallet_operation_request CHECK (
        request_amount > 0
        AND request_currency IN ('KRW', 'USD')
        AND request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_wallet_operation_status CHECK (
        status IN ('SUCCEEDED', 'REJECTED', 'BLOCKED_FUNDS')
    ),
    CONSTRAINT ck_wallet_operation_outcome CHECK (
        (
            status = 'SUCCEEDED'
            AND operation_group_id IS NOT NULL
            AND completed_at IS NOT NULL
            AND failure_code IS NULL
            AND failure_http_status IS NULL
            AND failure_title IS NULL
            AND failure_detail IS NULL
        )
        OR (
            status = 'REJECTED'
            AND operation_group_id IS NULL
            AND completed_at IS NOT NULL
            AND failure_code IS NOT NULL
            AND failure_http_status BETWEEN 400 AND 499
            AND failure_title IS NOT NULL
            AND failure_detail IS NOT NULL
        )
        OR (
            status = 'BLOCKED_FUNDS'
            AND operation_kind = 'BET_ADJUSTMENT'
            AND operation_group_id IS NULL
            AND completed_at IS NULL
            AND failure_code IS NULL
            AND failure_http_status IS NULL
            AND failure_title IS NULL
            AND failure_detail IS NULL
        )
    ),
    CONSTRAINT uk_wallet_operation_key_group
        UNIQUE (idempotency_key, operation_group_id)
);

CREATE INDEX ix_wallet_operation_user_requested
    ON wallet_operation (user_id, requested_at);
