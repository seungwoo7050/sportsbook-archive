package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletAdjustmentProof;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class RejectedProofTest {

  @Test
  void storesARejectedProofWithoutQueueOrLedgerEvidence() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.MAX);

    assertThat(
            new RevisionPlanRepository(jdbc)
                .markRejected(UUID.randomUUID(), lease, rejected(), Instant.EPOCH))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), values.capture());
    assertThat(sql.getValue())
        .contains(
            "state = 'REJECTED'",
            "wallet_status = 'REJECTED'",
            "lease_token = null",
            "next_retry_at = null",
            "lease_until > current_timestamp");
    assertThat(values.getValue()[2]).isEqualTo(lease.token());
  }

  private static WalletAdjustmentProof rejected() {
    return new WalletAdjustmentProof(
        UUID.randomUUID(),
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        Money.krw(200),
        Money.krw(100),
        -100,
        Currency.KRW,
        WalletAdjustmentProof.Status.REJECTED,
        null,
        null,
        null,
        null,
        null);
  }
}
