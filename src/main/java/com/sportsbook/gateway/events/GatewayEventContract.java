package com.sportsbook.gateway.events;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.OddsChanged;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Binds raw Kafka partition keys to canonical identities in decoded event payloads. */
public final class GatewayEventContract {

  public static OddsChanged oddsChanged(ConsumerRecord<byte[], byte[]> record) {
    OddsChanged event = StrictAvroDecoder.decode(record.value(), OddsChanged.class);
    requireCanonicalUuid(event.getEventId(), "eventId");
    requireCanonicalUuid(event.getMarketId(), "marketId");
    requireCanonicalUuid(event.getSelectionId(), "selectionId");
    requirePartitionKey(record.key(), event.getEventId(), record.topic());
    return event;
  }

  public static BetSettled betSettled(ConsumerRecord<byte[], byte[]> record) {
    BetSettled event = StrictAvroDecoder.decode(record.value(), BetSettled.class);
    requireBetIdentity(event.getBetId(), event.getUserId(), event.getEventId());
    requirePartitionKey(record.key(), event.getEventId(), record.topic());
    return event;
  }

  public static BetVoided betVoided(ConsumerRecord<byte[], byte[]> record) {
    BetVoided event = StrictAvroDecoder.decode(record.value(), BetVoided.class);
    requireBetIdentity(event.getBetId(), event.getUserId(), event.getEventId());
    requirePartitionKey(record.key(), event.getEventId(), record.topic());
    return event;
  }

  public static BetResolutionRevised betResolutionRevised(ConsumerRecord<byte[], byte[]> record) {
    BetResolutionRevised event =
        StrictAvroDecoder.decode(record.value(), BetResolutionRevised.class);
    requireCanonicalUuid(event.getRevisionId(), "revisionId");
    requireBetIdentity(event.getBetId(), event.getUserId(), event.getEventId());
    if (event.getRevisionNumber() < 1) {
      throw new GatewayEventContractException("revisionNumber must be positive");
    }
    if (!event.getPreviousPayout().getCurrency().equals(event.getNewPayout().getCurrency())) {
      throw new GatewayEventContractException("revision payout currencies must match");
    }
    if (event.getSourceResultSettledAt().isAfter(event.getRevisedAt())) {
      throw new GatewayEventContractException("revisedAt cannot precede sourceResultSettledAt");
    }
    requirePartitionKey(record.key(), event.getBetId(), record.topic());
    return event;
  }

  private static void requireBetIdentity(String betId, String userId, String eventId) {
    requireCanonicalUuid(betId, "betId");
    requireCanonicalUuid(userId, "userId");
    requireCanonicalUuid(eventId, "eventId");
  }

  private static void requirePartitionKey(byte[] rawKey, String expected, String topic) {
    if (rawKey == null) {
      throw new GatewayEventContractException("Kafka key is required for " + topic);
    }
    String actual = decodeUtf8(rawKey, topic);
    if (!expected.equals(actual)) {
      throw new GatewayEventContractException(
          "Kafka key does not match payload identity for " + topic);
    }
  }

  private static String decodeUtf8(byte[] rawKey, String topic) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(rawKey))
          .toString();
    } catch (CharacterCodingException failure) {
      throw new GatewayEventContractException("Kafka key is not valid UTF-8 for " + topic, failure);
    }
  }

  private static void requireCanonicalUuid(String value, String field) {
    if (value == null) {
      throw new GatewayEventContractException(field + " is required");
    }
    try {
      UUID parsed = UUID.fromString(value);
      if (!parsed.toString().equals(value)) {
        throw new GatewayEventContractException(field + " must be a canonical UUID");
      }
    } catch (IllegalArgumentException failure) {
      throw new GatewayEventContractException(field + " must be a canonical UUID", failure);
    }
  }

  private GatewayEventContract() {}
}
