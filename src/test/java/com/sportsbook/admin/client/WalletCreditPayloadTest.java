package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletCreditPayloadTest {

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void serializesTheCompleteWalletRefundMeaning() throws Exception {
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000101");

    JsonNode actual =
        json.readTree(json.writeValueAsBytes(WalletCreditPayload.refund(userId, Money.krw(750))));

    assertThat(actual)
        .isEqualTo(
            json.readTree(
                """
                {
                  "userId":"018f0000-0000-7000-8000-000000000101",
                  "amount":{"amount":750,"currency":"KRW"},
                  "source":"HOUSE_POOL",
                  "reason":"REFUND"
                }
                """));
  }
}
