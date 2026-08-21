package com.sportsbook.gateway.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

@SpringBootTest(
    properties = {
      "spring.main.web-application-type=none",
      "management.tracing.enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "logging.level.kafka=ERROR",
      "logging.level.org.apache.kafka=ERROR",
      "logging.level.org.springframework.kafka=ERROR"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = "gateway.retry.test",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class GatewayTransientRetryIntegrationTest {

  @Autowired private KafkaTemplate<byte[], byte[]> kafka;
  @Autowired private KafkaListenerEndpointRegistry listeners;
  @Autowired private FailureProbe probe;

  @BeforeEach
  void awaitProbe() {
    probe.reset();
    ContainerTestUtils.waitForAssignment(
        listeners.getListenerContainer("transient-retry-probe"), 1);
  }

  @Test
  void retriesTwiceAtOneSecondBeforeTheThirdAttemptSucceeds() throws Exception {
    kafka
        .send("gateway.retry.test", "key".getBytes(StandardCharsets.UTF_8), new byte[] {1, 2, 3})
        .get(5, TimeUnit.SECONDS);

    assertThat(probe.completed.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(probe.attempts).hasSize(3);
    assertThat(elapsed(probe.attempts, 0)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
    assertThat(elapsed(probe.attempts, 1)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
  }

  private static Duration elapsed(List<Long> attempts, int first) {
    return Duration.ofNanos(attempts.get(first + 1) - attempts.get(first));
  }

  @TestConfiguration
  static class ProbeConfiguration {

    @Bean
    FailureProbe failureProbe() {
      return new FailureProbe();
    }
  }

  static final class FailureProbe {

    private final List<Long> attempts = new CopyOnWriteArrayList<>();
    private CountDownLatch completed = new CountDownLatch(1);

    @KafkaListener(
        id = "transient-retry-probe",
        topics = "gateway.retry.test",
        groupId = "gateway-retry-test",
        autoStartup = "true")
    void receive(byte[] ignored) {
      attempts.add(System.nanoTime());
      if (attempts.size() < 3) {
        throw new IllegalStateException("temporary delivery failure");
      }
      completed.countDown();
    }

    void reset() {
      attempts.clear();
      completed = new CountDownLatch(1);
    }
  }
}
