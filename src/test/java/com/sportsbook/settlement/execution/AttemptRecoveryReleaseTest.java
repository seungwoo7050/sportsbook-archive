package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AttemptRecoveryReleaseTest {

  @Test
  void clearsExactLeaseAndStoresOnlySanitizedFailureIdentity() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    SettlementAttemptRepository repository = new SettlementAttemptRepository(jdbc);
    SettlementAttempt attempt = attempt();

    assertThat(
            repository.releaseForRecovery(
                attempt, WalletFailurePolicy.malformedSuccess(), Instant.EPOCH.plusSeconds(1)))
        .isTrue();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), parameters.capture());
    assertThat(sql.getValue())
        .contains("lease_token = null", "where bet_id = ? and lease_token = ?");
    assertThat(parameters.getValue()[0]).isEqualTo("WalletFailure:WALLET_MALFORMED_RESPONSE");
    assertThat(parameters.getValue()[1]).isInstanceOf(Timestamp.class);
  }

  private static SettlementAttempt attempt() {
    return SettlementAttempt.resolved(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SettlementResult.WON,
        new SettlementMoneyPlan(
            Money.krw(1000), Money.krw(2000), Money.krw(1000), Money.krw(0), Money.krw(1000)),
        new SettlementLease(UUID.randomUUID(), Instant.MAX),
        Instant.EPOCH);
  }
}
