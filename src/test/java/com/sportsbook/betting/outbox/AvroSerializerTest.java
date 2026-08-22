package com.sportsbook.betting.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.Money;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AvroSerializerTest {

  @Test
  void roundTripsOneRecordAndRejectsTrailingBytes() {
    Money money = Money.newBuilder().setAmount(1_000L).setCurrency("KRW").build();
    byte[] encoded = AvroSerializer.serialize(money);

    assertThat(AvroSerializer.deserialize(encoded, Money.class)).isEqualTo(money);

    byte[] tainted = Arrays.copyOf(encoded, encoded.length + 1);
    assertThatThrownBy(() -> AvroSerializer.deserialize(tainted, Money.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Trailing");
  }
}
