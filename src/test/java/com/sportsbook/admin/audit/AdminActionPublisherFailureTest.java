package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.security.AdminRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class AdminActionPublisherFailureTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  @Test
  void containsASynchronousBrokerFailure() {
    KafkaTemplate<String, byte[]> kafka = kafkaTemplate();
    when(kafka.send(eq("admin.action"), eq("operator-1"), any(byte[].class)))
        .thenThrow(new IllegalStateException("broker unavailable"));
    var publisher = new AdminActionPublisher(kafka, meters, "admin.action");

    assertThatCode(() -> publisher.publish(terminal())).doesNotThrowAnyException();

    assertThat(meters.counter("admin.audit.publish.failure").count()).isEqualTo(1);
    verify(kafka).send(eq("admin.action"), eq("operator-1"), any(byte[].class));
  }

  @Test
  void containsAnAsynchronousBrokerFailure() {
    KafkaTemplate<String, byte[]> kafka = kafkaTemplate();
    CompletableFuture<SendResult<String, byte[]>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new IllegalStateException("ack lost"));
    when(kafka.send(eq("admin.action"), eq("operator-1"), any(byte[].class))).thenReturn(failed);
    var publisher = new AdminActionPublisher(kafka, meters, "admin.action");

    assertThatCode(() -> publisher.publish(terminal())).doesNotThrowAnyException();

    assertThat(meters.counter("admin.audit.publish.failure").count()).isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, byte[]> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }

  private static AuditTerminalRecord terminal() {
    Instant started = Instant.parse("2026-08-22T01:02:03Z");
    return new AuditTerminalRecord(
        UUID.fromString("018f0000-0000-7000-8000-000000000094"),
        "operator-1",
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.SUCCESS,
        202,
        "operator request",
        "trace-1",
        started,
        started.plusSeconds(1));
  }
}
