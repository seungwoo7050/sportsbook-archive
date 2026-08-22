package com.sportsbook.betting.settlement;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SettlementResultListener {

  private final BetSettlementService settlement;

  public SettlementResultListener(BetSettlementService settlement) {
    this.settlement = settlement;
  }

  @KafkaListener(
      topics = {
        BettingTopics.BET_SETTLED,
        BettingTopics.BET_VOIDED,
        BettingTopics.BET_RESOLUTION_REVISED
      },
      groupId = "betting-resolution")
  public void onResolution(ConsumerRecord<String, byte[]> record) throws NoSuchAlgorithmException {
    String hash = sha256(record.value());
    switch (record.topic()) {
      case BettingTopics.BET_SETTLED -> {
        BetSettled event = AvroSerializer.deserialize(record.value(), BetSettled.class);
        requireKey(record, event.getEventId());
        settlement.apply(event, hash);
      }
      case BettingTopics.BET_VOIDED -> {
        BetVoided event = AvroSerializer.deserialize(record.value(), BetVoided.class);
        requireKey(record, event.getEventId());
        settlement.apply(event, hash);
      }
      case BettingTopics.BET_RESOLUTION_REVISED -> {
        BetResolutionRevised event =
            AvroSerializer.deserialize(record.value(), BetResolutionRevised.class);
        requireKey(record, event.getBetId());
        settlement.apply(event, hash);
      }
      default -> throw new IllegalArgumentException("Unsupported resolution topic");
    }
  }

  private static void requireKey(ConsumerRecord<String, byte[]> record, String eventId) {
    if (!eventId.equals(record.key())) {
      throw new IllegalArgumentException("Resolution Kafka key does not match eventId");
    }
  }

  private static String sha256(byte[] value) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }
}
