package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletResponseWireTest {

  @Test
  void readsRfc9457ErrorCodeProperty() throws Exception {
    WalletProblem problem =
        new ObjectMapper()
            .readValue(
                "{\"type\":\"urn:problem:wallet:operation-not-found\","
                    + "\"title\":\"Wallet operation not found\",\"status\":404,"
                    + "\"detail\":\"missing\",\"instance\":\"/internal/v1/wallet/"
                    + "transactions/debit/00000000-0000-4000-8000-000000000001\","
                    + "\"errorCode\":\"WALLET_OPERATION_NOT_FOUND\"}",
                WalletProblem.class);

    assertThat(problem.errorCode()).isEqualTo("WALLET_OPERATION_NOT_FOUND");
    assertThat(problem.detail()).isEqualTo("missing");
  }

  @Test
  void readsOperationIdFromFullSuccessBody() throws Exception {
    String operationId = "10000000-0000-4000-8000-000000000001";
    WalletOperationResponse response =
        new ObjectMapper()
            .findAndRegisterModules()
            .readValue(
                "{\"operationGroupId\":\""
                    + operationId
                    + "\",\"userId\":\"20000000-0000-4000-8000-000000000001\","
                    + "\"amount\":{\"amount\":1000,\"currency\":\"KRW\"},"
                    + "\"reason\":\"BET_DEBIT\",\"at\":\"2026-08-22T00:00:00Z\"}",
                WalletOperationResponse.class);

    assertThat(response.operationGroupId().toString()).isEqualTo(operationId);
    assertThat(response.userId().toString()).isEqualTo("20000000-0000-4000-8000-000000000001");
    assertThat(response.amount().amount()).isEqualTo(1_000L);
    assertThat(response.amount().currency().name()).isEqualTo("KRW");
    assertThat(response.reason()).isEqualTo("BET_DEBIT");
    assertThat(response.at()).isEqualTo(Instant.parse("2026-08-22T00:00:00Z"));
  }
}
