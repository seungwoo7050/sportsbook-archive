package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WalletAdjustmentIdentityTest {

  @Test
  void exposesImmutableRevisionRequestIdentity() {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000116");
    UUID betId = UUID.fromString("019b76da-a000-7000-8000-000000000117");
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000118");
    WalletAdjustment proof = new WalletAdjustment();
    ReflectionTestUtils.setField(proof, "revisionId", revisionId);
    ReflectionTestUtils.setField(proof, "idempotencyKey", "settlement:revision:" + revisionId);
    ReflectionTestUtils.setField(proof, "betId", betId);
    ReflectionTestUtils.setField(proof, "revisionNumber", 1L);
    ReflectionTestUtils.setField(proof, "userId", userId);
    ReflectionTestUtils.setField(proof, "previousPayoutAmount", 700L);
    ReflectionTestUtils.setField(proof, "newPayoutAmount", 1_000L);
    ReflectionTestUtils.setField(proof, "deltaAmount", 300L);
    ReflectionTestUtils.setField(proof, "currency", Currency.KRW);

    assertThat(proof.revisionId()).isEqualTo(revisionId);
    assertThat(proof.idempotencyKey()).isEqualTo("settlement:revision:" + revisionId);
    assertThat(proof.betId()).isEqualTo(betId);
    assertThat(proof.revisionNumber()).isEqualTo(1L);
    assertThat(proof.userId()).isEqualTo(userId);
    assertThat(proof.previousPayout()).isEqualTo(Money.krw(700L));
    assertThat(proof.newPayout()).isEqualTo(Money.krw(1_000L));
    assertThat(proof.deltaAmount()).isEqualTo(300L);
    assertThat(proof.currency()).isEqualTo(Currency.KRW);
  }
}
