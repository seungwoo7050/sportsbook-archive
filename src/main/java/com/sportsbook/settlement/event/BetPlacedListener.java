package com.sportsbook.settlement.event;

import com.sportsbook.protocol.event.BetPlacedRequested;
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

  @Autowired
  public BetPlacedListener(BetReadModelWriter writer) {
    this(writer, new StrictAvroDecoder(), new KafkaUuidKeyValidator(), new BetPlacedMapper());
  }

  BetPlacedListener(
      BetReadModelWriter writer,
      StrictAvroDecoder decoder,
      KafkaUuidKeyValidator keys,
      BetPlacedMapper mapper) {
    this.writer = writer;
    this.decoder = decoder;
    this.keys = keys;
    this.mapper = mapper;
  }

  @KafkaListener(
      topics = "${settlement.topics.bet-placed:bet.placed.v1}",
      groupId = "settlement-service")
  public void receive(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
    BetPlacedRequested event = decoder.decode(record.value(), BetPlacedRequested.class);
    keys.requireMatching(record.key(), event.getUserId(), "userId");
    writer.record(mapper.map(event));
    acknowledgment.acknowledge();
  }
}
