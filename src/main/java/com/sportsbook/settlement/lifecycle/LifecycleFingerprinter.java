package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class LifecycleFingerprinter {

  public String fingerprint(
      UUID eventId, EventLifecycleStatus status, Instant occurredAt, Instant scheduledStartAt) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      add(digest, "event-lifecycle-v1");
      add(digest, eventId.toString());
      add(digest, status.name());
      add(digest, occurredAt.toString());
      add(digest, scheduledStartAt == null ? "" : scheduledStartAt.toString());
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK must provide SHA-256", exception);
    }
  }

  private static void add(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
