package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyRecordTest {

  @Test
  void moneyRecordPreservesMinorUnitsAndCurrency() throws Exception {
    Money expected = Money.newBuilder().setAmount(12_345).setCurrency("KRW").build();
    AvroRecordTestSupport.assertFields(Money.getClassSchema(), "amount", "currency");
    assertThat(AvroRecordTestSupport.roundTrip(expected, Money.class)).isEqualTo(expected);
  }
}
