package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SourceRevisionMigrationTest {

  @Test
  void addsMonotonicBetRevisionsAndCandidateSourceIdentity() throws IOException {
    String sql;
    try (var stream = getClass().getResourceAsStream("/db/migration/V8__source_revision.sql")) {
      assertThat(stream).isNotNull();
      sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(sql).contains("revision_number BIGINT NOT NULL DEFAULT 0");
    assertThat(sql).contains("CHECK (revision_number >= 0)");
    assertThat(sql).contains("source_candidate_id UUID REFERENCES result_candidate");
    assertThat(sql).contains("ix_bet_selection_stale_source");
  }
}
