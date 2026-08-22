package com.sportsbook.settlement.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.settlement.lifecycle.LifecycleFanout;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import com.sportsbook.settlement.readmodel.BetPlacement;
import com.sportsbook.settlement.readmodel.BetReadModelWriter;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import java.util.List;
import java.util.UUID;
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
  private final AcceptedResultRepository acceptedResults;
  private final ResultFanout resultFanout;

  @Autowired
  public BetPlacedListener(
      BetReadModelWriter writer,
      LifecycleStore lifecycles,
      LifecycleFanout lifecycleFanout,
      AcceptedResultRepository acceptedResults,
      ResultFanout resultFanout) {
    this(
        writer,
        new StrictAvroDecoder(),
        new KafkaUuidKeyValidator(),
        new BetPlacedMapper(),
        lifecycles,
        lifecycleFanout,
        acceptedResults,
        resultFanout);
  }

  BetPlacedListener(
      BetReadModelWriter writer,
      StrictAvroDecoder decoder,
      KafkaUuidKeyValidator keys,
      BetPlacedMapper mapper,
      LifecycleStore lifecycles,
      LifecycleFanout lifecycleFanout,
      AcceptedResultRepository acceptedResults,
      ResultFanout resultFanout) {
    this.writer = writer;
    this.decoder = decoder;
    this.keys = keys;
    this.mapper = mapper;
    this.lifecycles = lifecycles;
    this.lifecycleFanout = lifecycleFanout;
    this.acceptedResults = acceptedResults;
    this.resultFanout = resultFanout;
  }

  @KafkaListener(
      topics = "${settlement.topics.bet-placed:bet.placed.v1}",
      groupId = "settlement-service")
  public void receive(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
    BetPlacedRequested event = decoder.decode(record.value(), BetPlacedRequested.class);
    keys.requireMatching(record.key(), event.getUserId(), "userId");
    BetPlacement placement = mapper.map(event);
    writer.record(placement);
    List<UUID> eventIds =
        placement.selections().stream()
            .map(BetPlacement.Selection::eventId)
            .distinct()
            .sorted()
            .toList();
    eventIds.forEach(
        eventId -> lifecycles.findTombstone(eventId).ifPresent(lifecycleFanout::fanOut));
    eventIds.forEach(
        eventId -> acceptedResults.findByEventId(eventId).ifPresent(resultFanout::fanOut));
    acknowledgment.acknowledge();
  }
}
