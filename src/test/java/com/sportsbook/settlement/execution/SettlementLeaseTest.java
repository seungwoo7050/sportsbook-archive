package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SettlementLeaseTest {

  @Test
  void expiresAtTheStoredBoundary() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    SettlementLease lease = SettlementLease.acquire(now, Duration.ofSeconds(30));

    assertThat(lease.isExpiredAt(now.plusSeconds(29))).isFalse();
    assertThat(lease.isExpiredAt(now.plusSeconds(30))).isTrue();
  }

  @Test
  void rejectsNonpositiveLeaseDuration() {
    assertThatThrownBy(() -> SettlementLease.acquire(Instant.EPOCH, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
