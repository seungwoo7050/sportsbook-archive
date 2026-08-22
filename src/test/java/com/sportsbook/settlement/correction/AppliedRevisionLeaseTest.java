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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AppliedRevisionLeaseTest {

  @Test
  void consumesTheExactOwnerAndRetainsRecoveredWalletEvidence() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.EPOCH.plusSeconds(30));

    assertThat(
            new RevisionPlanRepository(jdbc)
                .markApplied(UUID.randomUUID(), lease, applied(), Instant.EPOCH.plusSeconds(3)))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), values.capture());
    assertThat(sql.getValue())
        .contains(
            "state = 'APPLIED'",
            "lease_token = null",
            "next_retry_at = null",
            "lease_until > current_timestamp");
    assertThat(values.getValue()[0]).isEqualTo("APPLIED");
    assertThat(values.getValue()[1]).isEqualTo(7L);
    assertThat(values.getValue()[2]).isInstanceOf(UUID.class);
    assertThat(values.getValue()[3]).isInstanceOf(Timestamp.class);
    assertThat(values.getValue()[8]).isEqualTo(lease.token());
  }

  private static WalletAdjustmentProof applied() {
    return new WalletAdjustmentProof(
        UUID.randomUUID(),
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        Money.krw(200),
        Money.krw(100),
        -100,
        Currency.KRW,
        WalletAdjustmentProof.Status.APPLIED,
        7L,
        UUID.randomUUID(),
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(2),
        null);
  }
}
