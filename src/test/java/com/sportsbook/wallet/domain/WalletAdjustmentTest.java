package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WalletAdjustmentTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-000000000116");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000117");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000118");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000119");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void snapshotsAnImmediatelyAppliedRevision() {
    WalletAdjustment proof = WalletAdjustment.applied(command(700L, 1_000L), GROUP_ID, NOW);

    assertIdentity(proof, 300L);
    assertThat(proof.status()).isEqualTo(AdjustmentStatus.APPLIED);
    assertThat(proof.operationGroupId()).isEqualTo(GROUP_ID);
    assertThat(proof.appliedAt()).isEqualTo(NOW);
    assertThat(proof.queueSequence()).isNull();
    assertThat(proof.queuedAt()).isNull();
    assertThat(proof.nextAttemptAt()).isNull();
  }

  @Test
  void snapshotsAQueuedNegativeRevision() {
    WalletAdjustment proof = WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW);

    assertIdentity(proof, -300L);
    assertThat(proof.status()).isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(proof.queueSequence()).isEqualTo(4L);
    assertThat(proof.queuedAt()).isEqualTo(NOW);
    assertThat(proof.nextAttemptAt()).isEqualTo(NOW);
    assertThat(proof.operationGroupId()).isNull();
  }

  @Test
  void preventsPositiveOrUnsequencedBlockedProofs() {
    assertThatThrownBy(() -> WalletAdjustment.blocked(command(700L, 1_000L), 1L, NOW))
        .hasMessage("Only negative adjustments can be blocked");
    assertThatThrownBy(() -> WalletAdjustment.blocked(command(1_000L, 700L), 0L, NOW))
        .hasMessage("Queue sequence must be positive");
  }

  @Test
  void snapshotsARejectedRevisionWithoutQueueOrLedgerState() {
    WalletAdjustment proof = WalletAdjustment.rejected(command(1_000L, 700L), NOW);

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.REJECTED);
    assertThat(proof.operationGroupId()).isNull();
    assertThat(proof.queueSequence()).isNull();
    assertThat(proof.appliedAt()).isNull();
  }

  @Test
  void advancesOnlyABlockedProofWithoutDelayingAnEarlierAttempt() {
    WalletAdjustment proof = WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW);
    ReflectionTestUtils.setField(proof, "nextAttemptAt", NOW.plusSeconds(60L));

    proof.wake(NOW.minusSeconds(5L));
    proof.wake(NOW.minusSeconds(1L));

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(proof.queueSequence()).isEqualTo(4L);
    assertThat(proof.queuedAt()).isEqualTo(NOW);
    assertThat(proof.nextAttemptAt()).isEqualTo(NOW.minusSeconds(5L));
    assertThat(proof.updatedAt()).isEqualTo(NOW.minusSeconds(1L));
    assertThatThrownBy(
            () -> WalletAdjustment.applied(command(700L, 1_000L), GROUP_ID, NOW).wake(NOW))
        .hasMessage("Only blocked adjustments can be woken");
  }

  @Test
  void backsOffAnUnderfundedHeadWithoutChangingItsQueueIdentity() {
    WalletAdjustment proof = WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW);
    Instant attemptedAt = NOW.minusSeconds(3L);
    Instant retryAt = NOW.minusSeconds(1L);

    proof.deferUntil(attemptedAt, retryAt);

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(proof.queueSequence()).isEqualTo(4L);
    assertThat(proof.queuedAt()).isEqualTo(NOW);
    assertThat(proof.retryCount()).isEqualTo(1);
    assertThat(proof.nextAttemptAt()).isEqualTo(retryAt);
    assertThat(proof.updatedAt()).isEqualTo(attemptedAt);
    assertThatThrownBy(() -> proof.deferUntil(NOW.minusSeconds(4L), NOW.minusSeconds(5L)))
        .hasMessage("Recovery retry timestamps are out of order");
    assertThat(proof.retryCount()).isEqualTo(1);
    assertThat(proof.nextAttemptAt()).isEqualTo(retryAt);
    assertThat(proof.updatedAt()).isEqualTo(attemptedAt);
  }

  @Test
  void completesRecoveryWhilePreservingItsFIFOHistory() {
    WalletAdjustment proof = WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW);
    proof.deferUntil(NOW.minusSeconds(3L), NOW.minusSeconds(1L));

    proof.completeRecovery(GROUP_ID, NOW.minusSeconds(4L));

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.APPLIED);
    assertThat(proof.operationGroupId()).isEqualTo(GROUP_ID);
    assertThat(proof.queueSequence()).isEqualTo(4L);
    assertThat(proof.queuedAt()).isEqualTo(NOW);
    assertThat(proof.retryCount()).isEqualTo(1);
    assertThat(proof.appliedAt()).isEqualTo(NOW.minusSeconds(4L));
    assertThat(proof.updatedAt()).isEqualTo(NOW.minusSeconds(4L));
    assertThat(proof.nextAttemptAt()).isNull();
  }

  @Test
  void rejectsANullRecoveryGroupWithoutChangingBlockedState() {
    WalletAdjustment proof = WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW);

    assertThatThrownBy(() -> proof.completeRecovery(null, NOW.minusSeconds(4L)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("groupId");

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(proof.operationGroupId()).isNull();
    assertThat(proof.appliedAt()).isNull();
    assertThat(proof.nextAttemptAt()).isEqualTo(NOW);
    assertThat(proof.updatedAt()).isEqualTo(NOW);
    assertThat(proof.retryCount()).isZero();
  }

  private void assertIdentity(WalletAdjustment proof, long delta) {
    assertThat(proof.revisionId()).isEqualTo(REVISION_ID);
    assertThat(proof.betId()).isEqualTo(BET_ID);
    assertThat(proof.revisionNumber()).isEqualTo(1L);
    assertThat(proof.userId()).isEqualTo(USER_ID);
    assertThat(proof.previousPayout()).isEqualTo(Money.krw(delta > 0L ? 700L : 1_000L));
    assertThat(proof.newPayout()).isEqualTo(Money.krw(delta > 0L ? 1_000L : 700L));
    assertThat(proof.deltaAmount()).isEqualTo(delta);
  }

  private AdjustmentCommand command(long previous, long next) {
    return new AdjustmentCommand(
        REVISION_ID,
        BET_ID,
        1L,
        USER_ID,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + REVISION_ID));
  }
}
