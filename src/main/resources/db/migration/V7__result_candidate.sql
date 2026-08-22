-- Every distinct MatchResult is immutable evidence before it can replace the accepted snapshot.

CREATE TABLE result_candidate (
    candidate_id          UUID                     PRIMARY KEY,
    candidate_sequence    BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    event_id              UUID                     NOT NULL,
    fingerprint           CHAR(64)                 NOT NULL,
    mode                  VARCHAR(16)              NOT NULL,
    settled_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    state                 VARCHAR(24)              NOT NULL,
    replaces_candidate_id UUID REFERENCES result_candidate (candidate_id),
    decided_at            TIMESTAMP WITH TIME ZONE,
    decision_reason       VARCHAR(256),
    CONSTRAINT uq_result_candidate_fingerprint UNIQUE (event_id, fingerprint),
    CONSTRAINT ck_result_candidate_mode CHECK (
        mode IN ('COMPLETED', 'ABANDONED', 'VOIDED')),
    CONSTRAINT ck_result_candidate_state CHECK (
        state IN ('PENDING', 'ACCEPTED', 'SUPERSEDED', 'REJECTED')),
    CONSTRAINT ck_result_candidate_decision CHECK (
        (state = 'PENDING' AND decided_at IS NULL)
        OR (state <> 'PENDING' AND decided_at IS NOT NULL))
);

CREATE TABLE result_candidate_selection (
    candidate_id UUID       NOT NULL REFERENCES result_candidate (candidate_id),
    selection_id UUID       NOT NULL,
    outcome      VARCHAR(8) NOT NULL,
    CONSTRAINT pk_result_candidate_selection PRIMARY KEY (candidate_id, selection_id),
    CONSTRAINT ck_result_candidate_outcome CHECK (
        outcome IN ('WON', 'LOST', 'PUSH', 'VOID'))
);

CREATE INDEX ix_result_candidate_review
    ON result_candidate (state, received_at, candidate_sequence);

CREATE INDEX ix_result_candidate_event_order
    ON result_candidate (event_id, candidate_sequence);

ALTER TABLE match_result
    ADD COLUMN accepted_candidate_id UUID REFERENCES result_candidate (candidate_id);

CREATE UNIQUE INDEX uq_match_result_accepted_candidate
    ON match_result (accepted_candidate_id) WHERE accepted_candidate_id IS NOT NULL;
