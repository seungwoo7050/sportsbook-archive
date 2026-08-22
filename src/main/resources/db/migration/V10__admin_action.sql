-- Record each authenticated operator command exactly once.

CREATE TABLE settlement_admin_action (
    idempotency_key     UUID                     PRIMARY KEY,
    action_kind        VARCHAR(32)              NOT NULL,
    target_id          UUID                     NOT NULL,
    request_fingerprint CHAR(64)                NOT NULL,
    outcome            VARCHAR(32)              NOT NULL,
    execution_token    UUID,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_settlement_admin_action_kind CHECK (
        action_kind IN ('CANDIDATE_APPROVE', 'CANDIDATE_REJECT', 'REVISION_RETRY')),
    CONSTRAINT ck_settlement_admin_action_fingerprint CHECK (
        request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_settlement_admin_action_outcome CHECK (
        (action_kind = 'CANDIDATE_APPROVE' AND outcome = 'CANDIDATE_APPROVED')
        OR (action_kind = 'CANDIDATE_REJECT' AND outcome = 'CANDIDATE_REJECTED')
        OR (action_kind = 'REVISION_RETRY' AND outcome = 'REVISION_RETRY_QUEUED')),
    CONSTRAINT ck_settlement_admin_action_execution CHECK (
        (action_kind = 'REVISION_RETRY' AND execution_token IS NOT NULL)
        OR (action_kind <> 'REVISION_RETRY' AND execution_token IS NULL)),
    CONSTRAINT ck_settlement_admin_action_time CHECK (completed_at >= created_at)
);

CREATE INDEX ix_settlement_admin_action_target
    ON settlement_admin_action (target_id, action_kind, created_at);

CREATE FUNCTION reject_settlement_admin_action_mutation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'settlement admin actions are append-only';
END;
$$;

CREATE TRIGGER settlement_admin_action_append_only
    BEFORE UPDATE OR DELETE ON settlement_admin_action
    FOR EACH ROW EXECUTE FUNCTION reject_settlement_admin_action_mutation();
