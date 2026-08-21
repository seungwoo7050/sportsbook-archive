package com.sportsbook.wallet.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOutboxDispatcher implements OutboxDispatcher {

  public static final String EVENT_ID_HEADER = "event-id";

  private final KafkaTemplate<String, byte[]> kafka;

  public KafkaOutboxDispatcher(KafkaTemplate<String, byte[]> kafka) {
    this.kafka = kafka;
  }

  @Override
  public CompletionStage<Void> dispatch(LeasedOutboxMessage message) {
    List<Header> headers =
        List.of(
            new RecordHeader(
                EVENT_ID_HEADER,
                message.lease().eventId().toString().getBytes(StandardCharsets.US_ASCII)));
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(
            message.topic(), null, null, message.partitionKey(), message.payload(), headers);
    return kafka.send(record).thenApply(result -> null);
  }
}
