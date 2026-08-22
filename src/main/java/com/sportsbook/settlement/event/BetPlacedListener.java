package com.sportsbook.settlement.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import com.sportsbook.settlement.readmodel.BetPlacement;
import com.sportsbook.settlement.readmodel.BetReadModelWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Records accepted placements and acknowledges only after the database transaction returns. */
@Component
public class BetPlacedListener {

  private final BetReadModelWriter writer;
  private final StrictAvroDecoder decoder;
  private final KafkaUuidKeyValidator keys;
  private final BetPlacedMapper mapper;
  private final LifecycleStore lifecycles;
  private final LifecycleFanout lifecycleFanout;

  @Autowired
  public BetPlacedListener(
      BetReadModelWriter writer, LifecycleStore lifecycles, LifecycleFanout lifecycleFanout) {
    this(
        writer,
        new StrictAvroDecoder(),
        new KafkaUuidKeyValidator(),
        new BetPlacedMapper(),
        lifecycles,
        lifecycleFanout);
  }

  BetPlacedListener(
      BetReadModelWriter writer,
      StrictAvroDecoder decoder,
      KafkaUuidKeyValidator keys,
      BetPlacedMapper mapper,
      LifecycleStore lifecycles,
      LifecycleFanout lifecycleFanout) {
    this.writer = writer;
    this.decoder = decoder;
    this.keys = keys;
    this.mapper = mapper;
    this.lifecycles = lifecycles;
    this.lifecycleFanout = lifecycleFanout;
  }

  @KafkaListener(
      topics = "${settlement.topics.bet-placed:bet.placed.v1}",
      groupId = "settlement-service")
  public void receive(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
    BetPlacedRequested event = decoder.decode(record.value(), BetPlacedRequested.class);
    keys.requireMatching(record.key(), event.getUserId(), "userId");
    BetPlacement placement = mapper.map(event);
    writer.record(placement);
    placement.selections().stream()
        .map(BetPlacement.Selection::eventId)
        .distinct()
        .sorted()
        .forEach(eventId -> lifecycles.findTombstone(eventId).ifPresent(lifecycleFanout::fanOut));
    acknowledgment.acknowledge();
  }
}
