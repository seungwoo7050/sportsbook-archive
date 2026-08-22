package com.sportsbook.betting.settlement;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.config.KafkaMessageValidator;
import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SettlementResultListener {

  static final String REVISION_GAP_METRIC = "betting.resolution.revision.gaps";

  private final BetSettlementService settlement;
  private final Counter revisionGaps;

  public SettlementResultListener(BetSettlementService settlement, MeterRegistry meters) {
    this.settlement = settlement;
    this.revisionGaps = Counter.builder(REVISION_GAP_METRIC).register(meters);
  }

  @KafkaListener(
      topics = {
        BettingTopics.BET_SETTLED,
        BettingTopics.BET_VOIDED,
        BettingTopics.BET_RESOLUTION_REVISED
      },
      groupId = "betting-resolution")
  public void onResolution(ConsumerRecord<byte[], byte[]> record) throws NoSuchAlgorithmException {
    if (record.value() == null) {
      throw new PermanentKafkaException("Resolution payload is required");
    }
    String hash = sha256(record.value());
    switch (record.topic()) {
      case BettingTopics.BET_SETTLED -> {
        BetSettled event = KafkaMessageValidator.decode(record.value(), BetSettled.class);
        KafkaMessageValidator.requireKey(record.key(), event.getEventId(), "Settlement eventId");
        settlement.apply(event, hash);
      }
      case BettingTopics.BET_VOIDED -> {
        BetVoided event = KafkaMessageValidator.decode(record.value(), BetVoided.class);
        if (event.getReason() == com.sportsbook.protocol.event.VoidReason.MARKET_VOID) {
          throw new PermanentKafkaException("MARKET_VOID must use a settled VOID result");
        }
        KafkaMessageValidator.requireKey(record.key(), event.getEventId(), "Void eventId");
        settlement.apply(event, hash);
      }
      case BettingTopics.BET_RESOLUTION_REVISED -> {
        BetResolutionRevised event =
            KafkaMessageValidator.decode(record.value(), BetResolutionRevised.class);
        KafkaMessageValidator.requireKey(record.key(), event.getBetId(), "Revision betId");
        Bet.RevisionApplyResult result = settlement.apply(event, hash);
        if (result == Bet.RevisionApplyResult.APPLIED_WITH_GAP) {
          revisionGaps.increment();
        }
      }
      default -> throw new PermanentKafkaException("Unsupported resolution topic");
    }
  }

  private static String sha256(byte[] value) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }
}
