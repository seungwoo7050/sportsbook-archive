package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.Money;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaMessageValidatorTest {

  @Test
  void acceptsExactRawUuidKeyAndStrictAvroBytes() {
    UUID id = UUID.randomUUID();
    Money money = Money.newBuilder().setAmount(1_000).setCurrency("KRW").build();

    KafkaMessageValidator.requireKey(
        id.toString().getBytes(StandardCharsets.US_ASCII), id.toString(), "betId");

    assertThat(KafkaMessageValidator.decode(AvroSerializer.serialize(money), Money.class))
        .isEqualTo(money);
  }

  @Test
  void classifiesMalformedOrMismatchedKeysAsPermanent() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                KafkaMessageValidator.requireKey(
                    UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII),
                    id.toString(),
                    "betId"))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("mismatch");
    assertThatThrownBy(
            () ->
                KafkaMessageValidator.requireKey(new byte[] {(byte) 0xff}, id.toString(), "betId"))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("canonical");
  }

  @Test
  void classifiesMalformedAvroAsPermanent() {
    assertThatThrownBy(() -> KafkaMessageValidator.decode(new byte[] {1}, Money.class))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("Invalid Money payload");
  }
}
