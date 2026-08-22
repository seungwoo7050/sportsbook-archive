package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRetryReceiptTest {

  private static final UUID KEY = UUID.fromString("018f0000-0000-7000-8000-000000000167");
  private static final Instant DUE = Instant.parse("2026-08-22T01:05:00Z");

  @Test
  void acceptsQueuedAndReplayReceipts() {
    SettlementRetryReceipt pending =
        receipt(
            KEY,
            SettlementRetryReceipt.Outcome.QUEUED,
            SettlementRevisionView.State.PENDING,
            0,
            DUE);
    SettlementRetryReceipt blocked =
        receipt(
            KEY,
            SettlementRetryReceipt.Outcome.QUEUED,
            SettlementRevisionView.State.BLOCKED,
            0,
            DUE);
    SettlementRetryReceipt replay =
        receipt(
            KEY,
            SettlementRetryReceipt.Outcome.REPLAY,
            SettlementRevisionView.State.APPLIED,
            3,
            null);

    assertThat(SettlementRetryReceipt.verify(KEY, pending)).isSameAs(pending);
    assertThat(SettlementRetryReceipt.verify(KEY, blocked)).isSameAs(blocked);
    assertThat(SettlementRetryReceipt.verify(KEY, replay)).isSameAs(replay);
  }

  @Test
  void rejectsMismatchedUnsafeAndImpossibleQueuedProofs() {
    UUID other = UUID.fromString("018f0000-0000-7000-8000-000000000168");
    List<SettlementRetryReceipt> invalid =
        List.of(
            receipt(
                other,
                SettlementRetryReceipt.Outcome.QUEUED,
                SettlementRevisionView.State.PENDING,
                0,
                DUE),
            receipt(
                KEY,
                SettlementRetryReceipt.Outcome.QUEUED,
                SettlementRevisionView.State.PENDING,
                1,
                DUE),
            receipt(
                KEY,
                SettlementRetryReceipt.Outcome.QUEUED,
                SettlementRevisionView.State.EXHAUSTED,
                0,
                DUE),
            receipt(
                KEY,
                SettlementRetryReceipt.Outcome.QUEUED,
                SettlementRevisionView.State.BLOCKED,
                0,
                null),
            receipt(
                KEY,
                SettlementRetryReceipt.Outcome.REPLAY,
                SettlementRevisionView.State.PENDING,
                13,
                null));

    assertThatThrownBy(() -> SettlementRetryReceipt.verify(KEY, null))
        .isInstanceOf(DownstreamContractException.class);
    invalid.forEach(
        receipt ->
            assertThatThrownBy(() -> SettlementRetryReceipt.verify(KEY, receipt))
                .isInstanceOf(DownstreamContractException.class));
  }

  private static SettlementRetryReceipt receipt(
      UUID key,
      SettlementRetryReceipt.Outcome outcome,
      SettlementRevisionView.State state,
      Integer attemptCount,
      Instant nextRetryAt) {
    return new SettlementRetryReceipt(key, outcome, state, attemptCount, nextRetryAt);
  }
}
