package com.sportsbook.orchestration.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;

public record FixtureRecord(
    String topic, String key, Integer partition, byte[] payload, String fingerprint) {
  private static final byte[] POISON_PAYLOAD = {(byte) 0x80};

  public FixtureRecord {
    validatePartition(partition);
    payload = payload.clone();
  }

  public static FixtureRecord fromJson(FixtureType type, Path jsonPath, Integer partition)
      throws IOException {
    FixtureEncoder.EncodedFixture encoded = FixtureEncoder.encode(type, jsonPath);
    return new FixtureRecord(
        type.topic(), encoded.key(), partition, encoded.payload(), type.fingerprint());
  }

  public static FixtureRecord poisonMatchResult(String eventId) {
    String canonicalEventId = UUID.fromString(eventId).toString();
    if (!canonicalEventId.equals(eventId)) {
      throw new IllegalArgumentException("eventId must be a canonical UUID");
    }
    return new FixtureRecord("match.result", eventId, 2, POISON_PAYLOAD, "malformed");
  }

  public ProducerRecord<String, byte[]> producerRecord() {
    return new ProducerRecord<>(topic, partition, key, payload());
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }

  private static void validatePartition(Integer partition) {
    if (partition != null && (partition < 0 || partition > 2)) {
      throw new IllegalArgumentException("partition must be between 0 and 2");
    }
  }
}
