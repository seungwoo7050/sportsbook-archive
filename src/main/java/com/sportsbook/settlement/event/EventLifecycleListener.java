package com.sportsbook.settlement.event;

import com.sportsbook.protocol.event.EventLifecycle;
import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleObservation;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class EventLifecycleListener {

  private final LifecycleStore store;
  private final LifecycleFanout fanout;
  private final Clock clock;
  private final StrictAvroDecoder decoder;
  private final KafkaUuidKeyValidator keys;

  @Autowired
  public EventLifecycleListener(LifecycleStore store, LifecycleFanout fanout, Clock clock) {
    this(store, fanout, clock, new StrictAvroDecoder(), new KafkaUuidKeyValidator());
  }

  EventLifecycleListener(
      LifecycleStore store,
      LifecycleFanout fanout,
      Clock clock,
      StrictAvroDecoder decoder,
      KafkaUuidKeyValidator keys) {
    this.store = store;
    this.fanout = fanout;
    this.clock = clock;
    this.decoder = decoder;
    this.keys = keys;
  }

  @KafkaListener(
      topics = "${settlement.topics.event-lifecycle:event.lifecycle}",
      groupId = "settlement-service")
  public void receive(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
    EventLifecycle event = decoder.decode(record.value(), EventLifecycle.class);
    UUID eventId = keys.requireMatching(record.key(), event.getEventId(), "eventId");
    LifecycleObservation observation =
        LifecycleObservation.observe(
            eventId,
            Objects.requireNonNull(event.getStatus(), "status"),
            Objects.requireNonNull(event.getOccurredAt(), "occurredAt"),
            event.getScheduledStartAt(),
            clock.instant());
    store.record(observation);
    store.findTombstone(eventId).ifPresent(fanout::fanOut);
    acknowledgment.acknowledge();
  }
}
