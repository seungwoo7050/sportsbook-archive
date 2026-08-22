package com.sportsbook.settlement.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.Money;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import org.junit.jupiter.api.Test;

class StrictAvroEncoderTest {

  private final StrictAvroEncoder encoder = new StrictAvroEncoder();

  @Test
  void producesDeterministicRawBytesAcceptedByTheStrictDecoder() {
    Money record = Money.newBuilder().setAmount(1_000L).setCurrency("KRW").build();

    byte[] first = encoder.encode(record);
    byte[] second = encoder.encode(record);

    assertThat(first).containsExactly(second);
    assertThat(new StrictAvroDecoder().decode(first, Money.class)).isEqualTo(record);
  }
}
