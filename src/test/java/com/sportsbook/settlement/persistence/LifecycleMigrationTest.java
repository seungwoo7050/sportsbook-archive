package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LifecycleMigrationTest {

  @Test
  void createsDeduplicatedObservationsAndNonexpiringTerminalLatch() throws Exception {
    String migration =
        Files.readString(Path.of("src/main/resources/db/migration/V6__event_lifecycle.sql"));

    assertThat(migration)
        .contains(
            "UNIQUE (event_id, fingerprint)",
            "event_id, occurred_at, fingerprint",
            "CREATE TABLE event_lifecycle_tombstone",
            "terminal_status IN ('CANCELLED', 'POSTPONED')",
            "Non-expiring first terminal latch");
    assertThat(migration).doesNotContain("expires_at", "ON DELETE CASCADE");
  }
}
