package com.sportsbook.wallet.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Versioned TLV writer used only for wallet request fingerprints. */
final class CanonicalRequestEncoder {
  private static final byte[] MAGIC =
      "sportsbook.wallet.operation".getBytes(StandardCharsets.US_ASCII);
  private static final int VERSION = 1;

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final DataOutputStream output = new DataOutputStream(bytes);

  CanonicalRequestEncoder() {
    try {
      output.write(MAGIC);
      output.writeByte(0);
      output.writeByte(VERSION);
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  CanonicalRequestEncoder text(int tag, String value) {
    return field(tag, value.getBytes(StandardCharsets.UTF_8));
  }

  CanonicalRequestEncoder uuid(int tag, UUID value) {
    try {
      byte[] encoded =
          java.nio.ByteBuffer.allocate(Long.BYTES * 2)
              .putLong(value.getMostSignificantBits())
              .putLong(value.getLeastSignificantBits())
              .array();
      return field(tag, encoded);
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException("Invalid UUID fingerprint field", failure);
    }
  }

  CanonicalRequestEncoder number(int tag, long value) {
    return field(tag, java.nio.ByteBuffer.allocate(Long.BYTES).putLong(value).array());
  }

  byte[] toByteArray() {
    return bytes.toByteArray();
  }

  private CanonicalRequestEncoder field(int tag, byte[] value) {
    try {
      output.writeByte(tag);
      output.writeInt(value.length);
      output.write(value);
      return this;
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
