-- Track the accepted result evidence used by each terminal selection.

ALTER TABLE bet
    ADD COLUMN revision_number BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_bet_revision_nonnegative CHECK (revision_number >= 0);

ALTER TABLE bet_selection
    ADD COLUMN source_candidate_id UUID REFERENCES result_candidate (candidate_id);

CREATE INDEX ix_bet_selection_stale_source
    ON bet_selection (event_id, source_candidate_id, bet_id);
