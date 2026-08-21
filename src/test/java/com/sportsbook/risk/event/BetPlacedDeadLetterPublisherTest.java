package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class BetPlacedDeadLetterPublisherTest {
  private final KafkaTemplate<String, byte[]> kafka = mock();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private BetPlacedDeadLetterPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher =
        new BetPlacedDeadLetterPublisher(kafka, new EventTopics(null, null, null, null), meters);
  }

  @Test
  void waitsForBrokerAcknowledgmentAndPreservesTheRejectedRecord() throws Exception {
    CompletableFuture<SendResult<String, byte[]>> future = mock();
    when(kafka.send(any(ProducerRecord.class))).thenReturn(future);
    byte[] payload = {4, 2};

    publisher.publishAndAwait("user-key", payload, BetPlacedFailureReason.KEY_MISMATCH);

    verify(future).get(10_000L, TimeUnit.MILLISECONDS);
    ArgumentCaptor<ProducerRecord<String, byte[]>> record = ArgumentCaptor.captor();
    verify(kafka).send(record.capture());
    assertThat(record.getValue().topic()).isEqualTo("bet.placed.v1.DLT");
    assertThat(record.getValue().key()).isEqualTo("user-key");
    assertThat(record.getValue().value()).isSameAs(payload);
    assertThat(reason(record.getValue())).isEqualTo("KEY_MISMATCH");
    assertThat(meters.counter("risk_bet_placed_dlt_total", "reason", "KEY_MISMATCH").count())
        .isEqualTo(1.0);
  }

  @Test
  void brokerFailurePropagatesWithoutRecordingACompletedPublication() throws Exception {
    CompletableFuture<SendResult<String, byte[]>> future = mock();
    when(kafka.send(any(ProducerRecord.class))).thenReturn(future);
    when(future.get(10_000L, TimeUnit.MILLISECONDS))
        .thenThrow(new ExecutionException(new IllegalStateException("broker unavailable")));

    assertThatThrownBy(
            () ->
                publisher.publishAndAwait(
                    "user-key", new byte[] {1}, BetPlacedFailureReason.MALFORMED_EVENT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("failed to publish bet.placed DLT")
        .hasRootCauseMessage("broker unavailable");
    assertThat(meters.counter("risk_bet_placed_dlt_total", "reason", "MALFORMED_EVENT").count())
        .isZero();
  }

  private static String reason(ProducerRecord<String, byte[]> record) {
    return new String(
        record.headers().lastHeader("risk-dlt-reason").value(), StandardCharsets.US_ASCII);
  }
}
