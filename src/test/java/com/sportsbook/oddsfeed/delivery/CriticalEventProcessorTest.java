package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CriticalDeliveryProperties;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

class CriticalEventProcessorTest {

  @Test
  void retainsFailedEventsAndAcknowledgesTheirRetry() {
    CriticalEvent event =
        CriticalEvent.lifecycle(
            new EventId(UUID.randomUUID()),
            EventLifecycleStatus.SCHEDULED,
            Instant.EPOCH,
            Instant.EPOCH);
    StubQueue queue = new StubQueue(event);
    RecoveringProcessor processor = new RecoveringProcessor(queue);

    processor.drain();
    assertThat(processor.isHealthy()).isFalse();
    assertThat(queue.acknowledgements).isZero();

    processor.drain();
    assertThat(processor.isHealthy()).isTrue();
    assertThat(queue.acknowledgements).isEqualTo(1);
    assertThat(queue.poll()).isEmpty();
  }

  private static final class RecoveringProcessor extends CriticalEventProcessor {
    private int attempts;

    private RecoveringProcessor(CriticalEventQueue queue) {
      super(queue, null, null, null);
    }

    @Override
    void apply(CriticalEvent event) {
      if (++attempts == 1) {
        throw new IllegalStateException("temporary failure");
      }
    }
  }

  private static final class StubQueue extends CriticalEventQueue {
    private final QueuedCriticalEvent queued;
    private int acknowledgements;

    private StubQueue(CriticalEvent event) {
      super(
          new StringRedisTemplate(),
          new ObjectMapper(),
          new CriticalDeliveryProperties("stream", "group", "consumer", 1, Duration.ZERO),
          new SimpleMeterRegistry());
      queued = new QueuedCriticalEvent(RecordId.of("1-0"), event, false);
    }

    @Override
    public List<QueuedCriticalEvent> poll() {
      return acknowledgements == 0 ? List.of(queued) : List.of();
    }

    @Override
    public void acknowledge(QueuedCriticalEvent event) {
      acknowledgements++;
    }
  }
}
