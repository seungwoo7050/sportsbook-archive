package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import com.sportsbook.oddsfeed.publisher.OddsFeedPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

class CriticalDeliveryHealthIndicatorTest {

  @Test
  void reportsEveryDeliveryDependencyAndPendingCount() throws java.io.IOException {
    StubQueue queue = new StubQueue(true, 3);
    var healthy =
        new CriticalDeliveryHealthIndicator(queue, new StubPublisher(true), new StubProcessor(true))
            .health();

    assertThat(healthy.getStatus()).isEqualTo(Status.UP);
    assertThat(healthy.getDetails())
        .containsEntry("redisStream", "UP")
        .containsEntry("kafkaPublisher", "UP")
        .containsEntry("criticalProcessor", "UP")
        .containsEntry("pendingRecords", 3L);

    var unavailable =
        new CriticalDeliveryHealthIndicator(
                queue, new StubPublisher(false), new StubProcessor(true))
            .health();
    assertThat(unavailable.getStatus()).isEqualTo(Status.DOWN);
    assertThat(unavailable.getDetails()).containsEntry("kafkaPublisher", "DOWN");

    var defaults =
        new YamlPropertySourceLoader()
            .load("runtime-defaults", new ClassPathResource("application.yml"))
            .get(0);
    assertThat(defaults.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,redis,criticalDelivery");
  }

  private static final class StubQueue extends CriticalEventQueue {
    private final boolean healthy;
    private final long pending;

    private StubQueue(boolean healthy, long pending) {
      super(
          new StringRedisTemplate(),
          new ObjectMapper(),
          new CriticalDeliveryProperties("stream", "group", "consumer", 1, Duration.ZERO),
          new SimpleMeterRegistry());
      this.healthy = healthy;
      this.pending = pending;
    }

    @Override
    public boolean isHealthy() {
      return healthy;
    }

    @Override
    public long pendingCount() {
      return pending;
    }
  }

  private static final class StubPublisher extends OddsFeedPublisher {
    private final boolean healthy;

    private StubPublisher(boolean healthy) {
      super(null, null, null, null);
      this.healthy = healthy;
    }

    @Override
    public boolean isHealthy() {
      return healthy;
    }
  }

  private static final class StubProcessor extends CriticalEventProcessor {
    private final boolean healthy;

    private StubProcessor(boolean healthy) {
      super(null, null, null, null);
      this.healthy = healthy;
    }

    @Override
    public boolean isHealthy() {
      return healthy;
    }
  }
}
