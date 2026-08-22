-- Persist the opaque lease proof before any wallet side effect.
ALTER TABLE bet
    ADD COLUMN risk_reservation_token VARCHAR(64);

ALTER TABLE bet
    ADD CONSTRAINT bet_risk_reservation_token_valid CHECK (
        risk_reservation_token IS NULL
        OR risk_reservation_token ~ '^[0-9a-f]{64}$'
    );

COMMENT ON COLUMN bet.risk_reservation_token IS
    'Opaque token required to commit the retained risk reservation; null only before reserve or for legacy rows.';
