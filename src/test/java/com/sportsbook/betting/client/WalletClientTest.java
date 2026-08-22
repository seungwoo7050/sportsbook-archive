package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.betting.error.InsufficientBalanceException;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletClientTest {

  private MockRestServiceServer server;
  private WalletClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://wallet");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new WalletClient(builder.build(), new WalletProblemMapper(new ObjectMapper()));
  }

  @Test
  void debitsCanonicalBetKeyAndFullExposure() {
    UUID betId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    server
        .expect(requestTo("http://wallet/internal/v1/wallet/transactions/debit"))
        .andExpect(header("Idempotency-Key", betId.toString()))
        .andExpect(jsonPath("$.amount.amount").value(6_000))
        .andRespond(
            withSuccess(
                proof(operationId, userId, 6_000, "BET_DEBIT"), MediaType.APPLICATION_JSON));

    assertThat(client.debit(betId, userId, Money.krw(6_000))).isEqualTo(operationId);
  }

  @Test
  void rejectsMismatchedDebitProof() {
    UUID userId = UUID.randomUUID();
    server
        .expect(requestTo("http://wallet/internal/v1/wallet/transactions/debit"))
        .andRespond(
            withSuccess(
                proof(UUID.randomUUID(), userId, 6_000, "BET_REFUND"), MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.debit(UUID.randomUUID(), userId, Money.krw(6_000)))
        .isInstanceOf(WalletRejectedException.class);
  }

  @Test
  void retainsInsufficientBalanceAsBusinessVerdict() {
    server
        .expect(requestTo("http://wallet/internal/v1/wallet/transactions/debit"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"errorCode\":\"WALLET_INSUFFICIENT_BALANCE\","
                        + "\"detail\":\"not enough\"}"));

    assertThatThrownBy(() -> client.debit(UUID.randomUUID(), UUID.randomUUID(), Money.krw(1_000)))
        .isInstanceOf(InsufficientBalanceException.class)
        .hasMessage("not enough");
  }

  private static String proof(UUID operationId, UUID userId, long amount, String reason) {
    return "{\"operationGroupId\":\""
        + operationId
        + "\",\"userId\":\""
        + userId
        + "\",\"amount\":{\"amount\":"
        + amount
        + ",\"currency\":\"KRW\"},\"reason\":\""
        + reason
        + "\",\"at\":\"2026-08-22T00:00:00Z\"}";
  }
}
