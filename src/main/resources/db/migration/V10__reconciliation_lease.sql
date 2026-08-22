-- Claim stale placement recovery exclusively without changing user-visible timestamps.
ALTER TABLE bet
    ADD COLUMN reconciliation_eligible_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reconciliation_claim_owner VARCHAR(128),
    ADD COLUMN reconciliation_claim_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE bet
    ADD CONSTRAINT bet_reconciliation_claim_pair CHECK (
        (reconciliation_claim_owner IS NULL AND reconciliation_claim_until IS NULL)
        OR
        (reconciliation_claim_owner IS NOT NULL AND reconciliation_claim_until IS NOT NULL)
    );

CREATE INDEX ix_bet_reconciliation_claim
    ON bet (
        reconciliation_eligible_at,
        reconciliation_requested_at,
        created_at,
        bet_id
    )
    WHERE status = 'PENDING';

COMMENT ON COLUMN bet.reconciliation_eligible_at IS
    'Database-time retry eligibility advanced when a worker claims recovery.';
COMMENT ON COLUMN bet.reconciliation_claim_owner IS
    'Instance-scoped owner fencing concurrent recovery workers.';
COMMENT ON COLUMN bet.reconciliation_claim_until IS
    'Database-time lease expiry permitting crash recovery.';
