package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MoneyJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializationContainsOnlyRecordComponents() throws Exception {
    assertThat(mapper.writeValueAsString(Money.krw(123_456)))
        .isEqualTo("{\"amount\":123456,\"currency\":\"KRW\"}");
  }

  @Test
  void jsonRoundTripPreservesMoney() throws Exception {
    Money original = Money.usd(12_345);
    assertThat(mapper.readValue(mapper.writeValueAsString(original), Money.class))
        .isEqualTo(original);
  }
}
