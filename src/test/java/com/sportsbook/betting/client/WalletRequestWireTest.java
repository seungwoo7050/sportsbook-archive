package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletRequestWireTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void usesOnlyBettingAuthorizedRefundMeaning() throws Exception {
    UUID userId = UUID.randomUUID();
    String json = mapper.writeValueAsString(WalletCreditRequest.refund(userId, Money.krw(1_000)));

    assertThat(json).contains("\"userId\":\"" + userId + "\"");
    assertThat(json).contains("\"source\":\"USER_LOCKED\"");
    assertThat(json).contains("\"reason\":\"REFUND\"");
  }
}
