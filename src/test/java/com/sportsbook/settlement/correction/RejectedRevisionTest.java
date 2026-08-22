package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.settlement.client.WalletFailurePolicy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;

class RejectedRevisionTest {

  @Test
  void quarantinesPermanentFailuresOutsideRecoveryScans() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(RevisionState.REJECTED));
    RevisionLease lease = new RevisionLease(UUID.randomUUID(), Instant.EPOCH.plusSeconds(30));

    assertThat(
            new RevisionPlanRepository(jdbc)
                .rejectPermanent(UUID.randomUUID(), lease, permanentFailure(), Instant.EPOCH))
        .contains(RevisionState.REJECTED);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc)
        .query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), values.capture());
    assertThat(sql.getValue()).contains("else 'REJECTED'", "lease_token = null");
    assertThat(values.getValue()[0]).isEqualTo("WALLET_IDEMPOTENCY_CONFLICT");
    assertThat(values.getValue()[3]).isEqualTo(lease.token());
  }

  private static WalletFailurePolicy.PermanentFailure permanentFailure() throws Exception {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatus.CONFLICT);
    when(response.getBody())
        .thenReturn(
            new ByteArrayInputStream(
                "{\"errorCode\":\"WALLET_IDEMPOTENCY_CONFLICT\"}"
                    .getBytes(StandardCharsets.UTF_8)));
    try {
      WalletFailurePolicy.throwFor(response);
      throw new AssertionError("Expected permanent Wallet failure");
    } catch (WalletFailurePolicy.PermanentFailure failure) {
      return failure;
    }
  }
}
