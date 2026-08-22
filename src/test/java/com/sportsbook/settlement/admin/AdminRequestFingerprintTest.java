package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRequestFingerprintTest {

  @Test
  void fingerprintsTheKindTargetAndCanonicalPayload() {
    UUID target = UUID.fromString("00000000-0000-0000-0000-000000000001");
    String first =
        AdminRequestFingerprint.create(AdminAction.Kind.CANDIDATE_REJECT, target, "BAD_RESULT");

    assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(
            AdminRequestFingerprint.create(AdminAction.Kind.CANDIDATE_REJECT, target, "BAD_RESULT"))
        .isEqualTo(first);
    assertThat(AdminRequestFingerprint.create(AdminAction.Kind.CANDIDATE_APPROVE, target, ""))
        .isNotEqualTo(first);
    assertThat(
            AdminRequestFingerprint.create(
                AdminAction.Kind.CANDIDATE_REJECT, UUID.randomUUID(), "BAD_RESULT"))
        .isNotEqualTo(first);
  }

  @Test
  void permitsExecutionTokensOnlyForRevisionRetries() {
    UUID token = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                new AdminAction(
                    UUID.randomUUID(),
                    AdminAction.Kind.CANDIDATE_APPROVE,
                    UUID.randomUUID(),
                    "a".repeat(64),
                    AdminAction.Outcome.CANDIDATE_APPROVED,
                    token,
                    Instant.EPOCH,
                    Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
