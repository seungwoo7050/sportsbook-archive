package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettlementRevisionMigrationTest {

  @Test
  void persistsImmutablePlansLeasesAndWalletEvidence() throws Exception {
    String migration =
        Files.readString(Path.of("src/main/resources/db/migration/V9__settlement_revision.sql"));

    assertThat(migration)
        .contains(
            "UNIQUE (bet_id, revision_number)",
            "UNIQUE (bet_id, source_candidate_id)",
            "state IN ('PENDING', 'BLOCKED', 'EXHAUSTED', 'APPLIED', 'REJECTED')",
            "lease_token IS NULL AND lease_until IS NULL",
            "state IN ('PENDING', 'BLOCKED')",
            "state = 'APPLIED' AND applied_at IS NOT NULL",
            "wallet_queue_sequence",
            "wallet_operation_group_id",
            "wallet_next_attempt_at",
            "wallet_status IS NOT NULL AND wallet_status = 'BLOCKED'",
            "wallet_operation_group_id IS NULL AND wallet_applied_at IS NULL",
            "wallet_status IS NOT NULL AND wallet_status = 'APPLIED'",
            "wallet_operation_group_id IS NOT NULL AND wallet_applied_at IS NOT NULL",
            "wallet_status IS NOT NULL AND wallet_status = 'REJECTED'",
            "wallet_queue_sequence > 0 AND new_payout_amount < previous_payout_amount",
            "next_retry_at           TIMESTAMP WITH TIME ZONE,",
            "attempt_count BETWEEN 0 AND 12",
            "ck_settlement_revision_schedule",
            "state = 'BLOCKED' AND next_retry_at IS NULL",
            "wallet_status IS NOT NULL AND wallet_status = 'BLOCKED'",
            "last_error_code IS NOT NULL",
            "state IN ('EXHAUSTED', 'APPLIED', 'REJECTED')",
            "unit_stake_amount       BIGINT                   NOT NULL",
            "ck_settlement_revision_slip",
            "wallet_status IS NULL",
            "CREATE TABLE settlement_revision_selection",
            "PRIMARY KEY (revision_id, selection_id)",
            "UNIQUE (revision_id, leg_index)",
            "odds         NUMERIC(9,4) NOT NULL",
            "outcome IN ('WON', 'LOST', 'PUSH', 'VOID')",
            "ix_settlement_revision_recovery");
  }
}
