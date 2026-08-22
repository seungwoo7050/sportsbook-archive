ALTER TABLE audit_log RENAME COLUMN occurred_at TO started_at;
ALTER TABLE audit_log ALTER COLUMN http_status DROP NOT NULL;
ALTER TABLE audit_log ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE audit_log
SET completed_at = started_at;

ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_outcome
    CHECK (
        (outcome = 'STARTED'
            AND completed_at IS NULL
            AND http_status IS NULL)
        OR
        (outcome IN ('SUCCESS', 'FAILED')
            AND completed_at IS NOT NULL
            AND http_status IS NOT NULL)
        OR
        (outcome = 'UNKNOWN'
            AND completed_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_audit_log_http_status
    CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    ADD CONSTRAINT chk_audit_log_completion_time
    CHECK (completed_at IS NULL OR completed_at >= started_at);

ALTER INDEX idx_audit_log_occurred_at
    RENAME TO idx_audit_log_started_at;
ALTER INDEX idx_audit_log_actor_time
    RENAME TO idx_audit_log_actor_started_at;

CREATE INDEX idx_audit_log_stale_started
    ON audit_log (started_at)
    WHERE outcome = 'STARTED';
