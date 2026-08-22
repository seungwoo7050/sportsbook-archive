package com.sportsbook.settlement.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.EventLifecycle;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleObservation;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import com.sportsbook.settlement.outbox.StrictAvroEncoder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;

class EventLifecycleListenerTest {

  private final LifecycleStore store = mock(LifecycleStore.class);
  private final LifecycleFanout fanout = mock(LifecycleFanout.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final Instant receivedAt = Instant.parse("2026-08-22T00:00:01Z");
  private final EventLifecycleListener listener =
      new EventLifecycleListener(store, fanout, Clock.fixed(receivedAt, ZoneOffset.UTC));

  @Test
  void persistsThenFansOutStoredTombstoneBeforeAcknowledgment() {
    UUID eventId = UUID.randomUUID();
    EventLifecycle event = event(eventId);
    LifecycleObservation tombstone =
        LifecycleObservation.observe(
            eventId, EventLifecycleStatus.CANCELLED, Instant.EPOCH, null, receivedAt);
    when(store.findTombstone(eventId)).thenReturn(Optional.of(tombstone));

    listener.receive(record(event, eventId), acknowledgment);

    InOrder order = inOrder(store, fanout, acknowledgment);
    order.verify(store).record(any(LifecycleObservation.class));
    order.verify(store).findTombstone(eventId);
    order.verify(fanout).fanOut(tombstone);
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void rejectsMismatchedRawEventKeyBeforePersistence() {
    EventLifecycle event = event(UUID.randomUUID());

    assertThatThrownBy(() -> listener.receive(record(event, UUID.randomUUID()), acknowledgment))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(store, fanout, acknowledgment);
  }

  @Test
  void marksTheProductionConstructorForDependencyInjection() throws NoSuchMethodException {
    var constructor =
        EventLifecycleListener.class.getConstructor(
            LifecycleStore.class, LifecycleFanout.class, Clock.class);

    assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
  }

  private static ConsumerRecord<byte[], byte[]> record(EventLifecycle event, UUID key) {
    return new ConsumerRecord<>(
        "event.lifecycle",
        0,
        0,
        key.toString().getBytes(UTF_8),
        new StrictAvroEncoder().encode(event));
  }

  private static EventLifecycle event(UUID eventId) {
    return EventLifecycle.newBuilder()
        .setEventId(eventId.toString())
        .setStatus(EventLifecycleStatus.CANCELLED)
        .setOccurredAt(Instant.EPOCH)
        .setScheduledStartAt(Instant.EPOCH.plusSeconds(3600))
        .build();
  }
}
