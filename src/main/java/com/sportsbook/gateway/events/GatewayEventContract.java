package com.sportsbook.gateway.events;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
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
