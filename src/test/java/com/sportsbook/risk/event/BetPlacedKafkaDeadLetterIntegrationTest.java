package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class BetPlacedKafkaDeadLetterIntegrationTest extends BetPlacedKafkaIntegrationSupport {
  @Test
  void malformedBinaryEventIsPublishedToTheDeadLetterTopic() throws Exception {
    byte[] malformed = {1, 2, 3};

    publish(BetPlacedEventFixture.USER_ID, malformed);

    ConsumerRecord<String, byte[]> deadLetter = consumeDeadLetter();
    assertThat(deadLetter.key()).isEqualTo(BetPlacedEventFixture.USER_ID);
    assertThat(deadLetter.value()).containsExactly(malformed);
    assertThat(
            new String(
                deadLetter.headers().lastHeader("risk-dlt-reason").value(),
                StandardCharsets.US_ASCII))
        .isEqualTo(BetPlacedFailureReason.MALFORMED_EVENT.name());
    verifyNoInteractions(reconciler);
  }
}
