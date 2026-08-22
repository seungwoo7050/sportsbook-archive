package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RevisionLeaseTest {

  @Test
  void expiresAtItsExactFence() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    RevisionLease lease = RevisionLease.acquire(now, Duration.ofSeconds(30));

    assertThat(lease.isExpiredAt(now.plusSeconds(29))).isFalse();
    assertThat(lease.isExpiredAt(now.plusSeconds(30))).isTrue();
  }

  @Test
  void rejectsNonpositiveDurations() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RevisionLease.acquire(Instant.EPOCH, Duration.ZERO));
  }
}
