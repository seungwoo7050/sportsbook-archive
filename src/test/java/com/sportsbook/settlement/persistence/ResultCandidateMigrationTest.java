package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResultCandidateMigrationTest {

  @Test
  void preservesImmutableCandidateIdentityAndReviewState() throws Exception {
    String migration =
        Files.readString(Path.of("src/main/resources/db/migration/V7__result_candidate.sql"));

    assertThat(migration)
        .contains(
            "UNIQUE (event_id, fingerprint)",
            "GENERATED ALWAYS AS IDENTITY",
            "state IN ('PENDING', 'ACCEPTED', 'SUPERSEDED', 'REJECTED')",
            "PRIMARY KEY (candidate_id, selection_id)",
            "accepted_candidate_id UUID REFERENCES result_candidate");
  }
}
