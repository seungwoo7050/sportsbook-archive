package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WalletAdjustmentLifecycleTest {

  @Test
  void exposesProofLifecycleMetadata() {
    WalletAdjustment proof = new WalletAdjustment();
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000119");
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    Instant updatedAt = createdAt.plusSeconds(1L);
    ReflectionTestUtils.setField(proof, "status", AdjustmentStatus.BLOCKED);
    ReflectionTestUtils.setField(proof, "queueSequence", 4L);
    ReflectionTestUtils.setField(proof, "operationGroupId", groupId);
    ReflectionTestUtils.setField(proof, "queuedAt", createdAt);
    ReflectionTestUtils.setField(proof, "appliedAt", updatedAt);
    ReflectionTestUtils.setField(proof, "nextAttemptAt", updatedAt);
    ReflectionTestUtils.setField(proof, "retryCount", 2);
    ReflectionTestUtils.setField(proof, "createdAt", createdAt);
    ReflectionTestUtils.setField(proof, "updatedAt", updatedAt);

    assertThat(proof.status()).isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(proof.queueSequence()).isEqualTo(4L);
    assertThat(proof.operationGroupId()).isEqualTo(groupId);
    assertThat(proof.queuedAt()).isEqualTo(createdAt);
    assertThat(proof.appliedAt()).isEqualTo(updatedAt);
    assertThat(proof.nextAttemptAt()).isEqualTo(updatedAt);
    assertThat(proof.retryCount()).isEqualTo(2);
    assertThat(proof.createdAt()).isEqualTo(createdAt);
    assertThat(proof.updatedAt()).isEqualTo(updatedAt);
  }
}
