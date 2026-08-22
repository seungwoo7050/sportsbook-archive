package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminActionReplayTest {

  @Test
  void acceptsOnlyTheSameKindTargetAndFingerprint() {
    UUID target = UUID.randomUUID();
    String fingerprint = "a".repeat(64);
    AdminAction existing = action(target, fingerprint);

    assertThat(
            AdminActionReplay.requireExact(
                Optional.of(existing), AdminAction.Kind.CANDIDATE_APPROVE, target, fingerprint))
        .contains(existing);
    assertThat(
            AdminActionReplay.requireExact(
                Optional.empty(), AdminAction.Kind.CANDIDATE_APPROVE, target, fingerprint))
        .isEmpty();
    assertThatThrownBy(
            () ->
                AdminActionReplay.requireExact(
                    Optional.of(existing), AdminAction.Kind.CANDIDATE_REJECT, target, fingerprint))
        .isInstanceOf(AdminControlException.class)
        .hasMessage("Idempotency-Key is already bound to another request");
  }

  private static AdminAction action(UUID target, String fingerprint) {
    return new AdminAction(
        UUID.randomUUID(),
        AdminAction.Kind.CANDIDATE_APPROVE,
        target,
        fingerprint,
        AdminAction.Outcome.CANDIDATE_APPROVED,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
