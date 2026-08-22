package com.sportsbook.settlement.event;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Validates a raw Kafka key as one strict canonical lowercase UUID. */
public final class KafkaUuidKeyValidator {

  private static final int UUID_TEXT_LENGTH = 36;

  public UUID requireMatching(byte[] rawKey, CharSequence recordField, String fieldName) {
    Objects.requireNonNull(fieldName, "fieldName");
    if (rawKey == null || rawKey.length != UUID_TEXT_LENGTH) {
      throw invalid(fieldName, "key must be 36 raw UTF-8 bytes");
    }
    String text = decode(rawKey, fieldName);
    UUID parsed;
    try {
      parsed = UUID.fromString(text);
    } catch (IllegalArgumentException exception) {
      throw invalid(fieldName, "key is not a UUID");
    }
    if (!parsed.toString().equals(text)) {
      throw invalid(fieldName, "key is not canonical lowercase UUID text");
    }
    if (recordField == null || !text.contentEquals(recordField)) {
      throw invalid(fieldName, "key does not match record field");
    }
    return parsed;
  }

  private static String decode(byte[] rawKey, String fieldName) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(rawKey))
          .toString();
    } catch (CharacterCodingException exception) {
      throw invalid(fieldName, "key is not strict UTF-8");
    }
  }

  private static IllegalArgumentException invalid(String field, String reason) {
    return new IllegalArgumentException("Invalid Kafka " + field + ": " + reason);
  }
}
