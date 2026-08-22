package com.sportsbook.betting.placement;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.WalletDebitFailed;
import com.sportsbook.protocol.event.WalletDebited;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WalletEventListener {

  private final WalletEventInbox inbox;
  private final BetPlacementService placement;

  public WalletEventListener(WalletEventInbox inbox, BetPlacementService placement) {
    this.inbox = inbox;
    this.placement = placement;
  }

  @KafkaListener(
      topics = {BettingTopics.WALLET_DEBITED, BettingTopics.WALLET_DEBIT_FAILED},
      groupId = "betting-wallet")
  public void onWalletEvent(ConsumerRecord<String, byte[]> record) throws NoSuchAlgorithmException {
    EventIdentity identity = identity(record);
    UUID eventId = eventId(record);
    inbox.record(
        eventId, record.topic(), identity.betId(), identity.userId(), sha256(record.value()));
    placement.reconcile(identity.betId());
    inbox.markProcessed(eventId);
  }

  private static EventIdentity identity(ConsumerRecord<String, byte[]> record) {
    String userId;
    String betId;
    if (BettingTopics.WALLET_DEBITED.equals(record.topic())) {
      WalletDebited event = AvroSerializer.deserialize(record.value(), WalletDebited.class);
      userId = event.getUserId();
      betId = event.getIdempotencyKey();
    } else if (BettingTopics.WALLET_DEBIT_FAILED.equals(record.topic())) {
      WalletDebitFailed event = AvroSerializer.deserialize(record.value(), WalletDebitFailed.class);
      userId = event.getUserId();
      betId = event.getIdempotencyKey();
    } else {
      throw new IllegalArgumentException("Unsupported wallet topic");
    }
    UUID user = canonical(userId, "userId");
    if (!userId.equals(record.key())) {
      throw new IllegalArgumentException("Wallet Kafka key does not match userId");
    }
    return new EventIdentity(canonical(betId, "betId"), user);
  }

  private static UUID eventId(ConsumerRecord<String, byte[]> record) {
    List<Header> values =
        StreamSupport.stream(record.headers().headers("event-id").spliterator(), false).toList();
    if (values.size() != 1) {
      throw new IllegalArgumentException("Exactly one event-id header is required");
    }
    return canonical(new String(values.get(0).value(), StandardCharsets.US_ASCII), "event-id");
  }

  private static UUID canonical(String value, String name) {
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equals(value)) {
      throw new IllegalArgumentException(name + " must be a canonical lowercase UUID");
    }
    return parsed;
  }

  private static String sha256(byte[] value) throws NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private record EventIdentity(UUID betId, UUID userId) {}
}
