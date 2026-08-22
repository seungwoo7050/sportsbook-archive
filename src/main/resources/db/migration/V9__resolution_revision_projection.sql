-- Store a verifiable full-snapshot resolution projection across base and revision topics.
ALTER TABLE bet
    ADD COLUMN resolution_event_id             UUID,
    ADD COLUMN resolution_revision_id          UUID,
    ADD COLUMN resolution_revision_number      BIGINT,
    ADD COLUMN resolution_payload_sha256       VARCHAR(64),
    ADD COLUMN source_result_settled_at         TIMESTAMP WITH TIME ZONE;

ALTER TABLE bet
    ADD CONSTRAINT bet_resolution_hash_valid CHECK (
        resolution_payload_sha256 IS NULL
        OR resolution_payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT bet_resolution_revision_valid CHECK (
        (resolution_revision_id IS NULL
            AND (resolution_revision_number IS NULL OR resolution_revision_number = 0))
        OR
        (resolution_revision_id IS NOT NULL AND resolution_revision_number >= 1)
    ),
    ADD CONSTRAINT bet_resolution_terminal_only CHECK (
        status IN ('SETTLED', 'VOIDED')
        OR (
            resolution_event_id IS NULL
            AND resolution_revision_id IS NULL
            AND resolution_revision_number IS NULL
            AND resolution_payload_sha256 IS NULL
            AND source_result_settled_at IS NULL
        )
    ),
    ADD CONSTRAINT bet_void_revision_forbidden CHECK (
        status <> 'VOIDED' OR resolution_revision_id IS NULL
    );

-- Null proof columns remain permitted for terminal rows that predate this migration.
CREATE UNIQUE INDEX uk_bet_resolution_revision
    ON bet (resolution_revision_id)
    WHERE resolution_revision_id IS NOT NULL;

COMMENT ON COLUMN bet.resolution_revision_number IS
    'Logical revision 0 for a base event and 1+ for a full replacement snapshot.';
COMMENT ON COLUMN bet.source_result_settled_at IS
    'Source result time used to compare and diagnose corrected settlement projections.';
