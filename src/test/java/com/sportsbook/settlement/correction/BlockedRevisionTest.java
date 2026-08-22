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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BlockedRevisionTest {

  @Test
  void clearsTheExactLeaseAndStoresQueueEvidence() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(RevisionState.BLOCKED));
    UUID revisionId = UUID.randomUUID();
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.EPOCH.plusSeconds(30));

    assertThat(
            new RevisionPlanRepository(jdbc)
                .markBlocked(revisionId, lease, blocked(revisionId), Instant.EPOCH))
        .contains(RevisionState.BLOCKED);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), values.capture());
    assertThat(sql.getValue())
        .contains(
            "attempt_count >= 12",
            "WALLET_RETRY_EXHAUSTED",
            "state = 'BLOCKED'",
            "wallet_status = 'BLOCKED'",
            "next_retry_at = case",
            "lease_token = null",
            "lease_until > current_timestamp",
            "returning state");
    assertThat(values.getValue()[0]).isEqualTo(7L);
    assertThat(values.getValue()[1]).isInstanceOf(Timestamp.class);
    assertThat(values.getValue()[6]).isEqualTo(lease.token());
  }

  private static WalletAdjustmentProof blocked(UUID revisionId) {
    return new WalletAdjustmentProof(
        revisionId,
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        Money.krw(200),
        Money.krw(100),
        -100,
        Currency.KRW,
        WalletAdjustmentProof.Status.BLOCKED,
        7L,
        null,
        Instant.EPOCH,
        null,
        Instant.EPOCH.plusSeconds(1));
  }
}
