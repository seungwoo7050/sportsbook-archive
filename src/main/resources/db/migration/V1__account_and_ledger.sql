CREATE TABLE account (
    user_id UUID PRIMARY KEY,
    available_amount BIGINT NOT NULL DEFAULT 0,
    available_currency VARCHAR(3) NOT NULL,
    locked_amount BIGINT NOT NULL DEFAULT 0,
    locked_currency VARCHAR(3) NOT NULL,
    recovery_debt_amount NUMERIC(38, 0) NOT NULL DEFAULT 0,
    recovery_frozen_at TIMESTAMPTZ,
    next_adjustment_sequence BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_user_uuid CHECK (
        user_id NOT IN (
            '00000000-0000-7000-8000-000000000001',
            '00000000-0000-7000-8000-000000000002'
        )
    ),
    CONSTRAINT ck_account_available_nonnegative CHECK (available_amount >= 0),
    CONSTRAINT ck_account_locked_nonnegative CHECK (locked_amount >= 0),
    CONSTRAINT ck_account_currency CHECK (
        available_currency = locked_currency
        AND available_currency IN ('KRW', 'USD')
    ),
    CONSTRAINT ck_account_aggregate_limit CHECK (
        available_amount <= 9223372036854775807 - locked_amount
    ),
    CONSTRAINT ck_account_recovery_debt CHECK (recovery_debt_amount >= 0),
    CONSTRAINT ck_account_recovery_freeze CHECK (
        (recovery_debt_amount = 0 AND recovery_frozen_at IS NULL)
        OR (recovery_debt_amount > 0 AND recovery_frozen_at IS NOT NULL)
    ),
    CONSTRAINT ck_account_adjustment_sequence CHECK (next_adjustment_sequence > 0)
);

CREATE TABLE ledger_entry (
    entry_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    bucket VARCHAR(16) NOT NULL,
    side VARCHAR(6) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_group_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ledger_bucket CHECK (bucket IN ('AVAILABLE', 'LOCKED')),
    CONSTRAINT ck_ledger_side CHECK (side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_currency CHECK (currency IN ('KRW', 'USD')),
    CONSTRAINT ck_ledger_reason CHECK (
        reason IN (
            'DEPOSIT', 'WITHDRAW', 'BET_DEBIT', 'BET_PAYOUT',
            'BET_REFUND', 'BET_FORFEIT', 'BET_ADJUSTMENT'
        )
    ),
    CONSTRAINT uk_ledger_entry_idempotency_side UNIQUE (idempotency_key, side),
    CONSTRAINT uk_ledger_entry_group_side UNIQUE (operation_group_id, side)
);

CREATE INDEX ix_ledger_entry_account_created
    ON ledger_entry (account_id, created_at);
CREATE INDEX ix_ledger_entry_idempotency_key
    ON ledger_entry (idempotency_key);
