package com.sportsbook.settlement.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.event.MatchResult;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.settlement.outbox.StrictAvroEncoder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class BaseResultEvents {

  private static final StrictAvroEncoder ENCODER = new StrictAvroEncoder();

  private BaseResultEvents() {}

  public static MatchResult voided(UUID eventId) {
    return MatchResult.newBuilder()
        .setEventId(eventId.toString())
        .setScore("0-0")
        .setFinalStatus(MatchFinalStatus.VOIDED)
        .setResultDetail(Map.of())
        .setSettledAt(Instant.EPOCH)
        .build();
  }

  public static MatchResult completed(UUID eventId, UUID selectionId, SettlementResult outcome) {
    return MatchResult.newBuilder()
        .setEventId(eventId.toString())
        .setScore("1-0")
        .setFinalStatus(MatchFinalStatus.COMPLETED)
        .setResultDetail(Map.of(selectionId.toString(), outcome.name()))
        .setSettledAt(Instant.EPOCH)
        .build();
  }

  public static BetPlacedRequested single(UUID betId, UUID userId, UUID eventId, UUID selectionId) {
    RequestedSelection selected =
        RequestedSelection.newBuilder()
            .setEventId(eventId.toString())
            .setMarketId(UUID.randomUUID().toString())
            .setSelectionId(selectionId.toString())
            .setOddsAtSubmission("2.0000")
            .build();
    return BetPlacedRequested.newBuilder()
        .setBetId(betId.toString())
        .setUserId(userId.toString())
        .setSlipType(BetSlipTypeTag.SINGLE)
        .setSystemMinWins(null)
        .setSystemTotalSelections(null)
        .setSelections(List.of(selected))
        .setStake(Money.newBuilder().setAmount(100).setCurrency("KRW").build())
        .setIdempotencyKey("placement-" + betId)
        .setRequestedAt(Instant.EPOCH)
        .build();
  }

  public static BetPlacedRequested multiple(
      UUID betId, UUID userId, Map<UUID, UUID> eventSelections) {
    List<RequestedSelection> selections =
        eventSelections.entrySet().stream()
            .map(entry -> selection(entry.getKey(), entry.getValue()))
            .toList();
    return BetPlacedRequested.newBuilder(
            single(betId, userId, UUID.randomUUID(), UUID.randomUUID()))
        .setSlipType(BetSlipTypeTag.MULTIPLE)
        .setSelections(selections)
        .build();
  }

  public static ConsumerRecord<byte[], byte[]> resultRecord(MatchResult event) {
    return record("match.result", event.getEventId(), ENCODER.encode(event));
  }

  public static ConsumerRecord<byte[], byte[]> placementRecord(BetPlacedRequested event) {
    return record("bet.placed.v1", event.getUserId(), ENCODER.encode(event));
  }

  private static ConsumerRecord<byte[], byte[]> record(
      String topic, CharSequence key, byte[] value) {
    return new ConsumerRecord<>(topic, 0, 0, key.toString().getBytes(UTF_8), value);
  }

  private static RequestedSelection selection(UUID eventId, UUID selectionId) {
    return RequestedSelection.newBuilder()
        .setEventId(eventId.toString())
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(selectionId.toString())
        .setOddsAtSubmission("2.0000")
        .build();
  }
}
