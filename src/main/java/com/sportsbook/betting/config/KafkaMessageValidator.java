package com.sportsbook.betting.config;

import com.sportsbook.betting.outbox.AvroSerializer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;

public final class KafkaMessageValidator {

  public static <T extends SpecificRecord> T decode(byte[] payload, Class<T> type) {
    try {
      return AvroSerializer.deserialize(payload, type);
    } catch (RuntimeException failure) {
      throw new PermanentKafkaException("Invalid " + type.getSimpleName() + " payload", failure);
    }
  }

  public static UUID canonical(String value, String name) {
    try {
      UUID parsed = UUID.fromString(value);
      if (!parsed.toString().equals(value)) {
        throw new IllegalArgumentException("not canonical");
      }
      return parsed;
    } catch (RuntimeException failure) {
      throw new PermanentKafkaException(name + " must be a canonical lowercase UUID", failure);
    }
  }

  public static void requireKey(byte[] rawKey, String expected, String name) {
    if (rawKey == null) {
      throw new PermanentKafkaException(name + " Kafka key is required");
    }
    String actual = new String(rawKey, StandardCharsets.US_ASCII);
    UUID actualId = canonical(actual, name + " Kafka key");
    if (!actualId.equals(canonical(expected, name)) || !actual.equals(expected)) {
      throw new PermanentKafkaException(name + " Kafka key mismatch");
    }
  }

  private KafkaMessageValidator() {}
}
