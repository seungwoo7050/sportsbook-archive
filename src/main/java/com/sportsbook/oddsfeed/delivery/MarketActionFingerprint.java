package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.IdempotencyKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Computes canonical hashes for requests and Redis identities. */
public final class MarketActionFingerprint {

  private static final String REQUEST_VERSION = "1";
  private static final String CALLER = "admin-api";
  private static final String IDEMPOTENCY_DOMAIN = "sportsbook-idempotency-key-v1";

  private MarketActionFingerprint() {}

  public static String request(
      UUID eventId, UUID marketId, MarketStatus requestedStatus, String reason) {
    MessageDigest digest = newDigest();
    updateString(digest, REQUEST_VERSION);
    updateString(digest, CALLER);
    updateString(digest, action(requestedStatus));
    updateString(digest, eventId.toString());
    updateString(digest, marketId.toString());
    updateString(digest, requestedStatus.name());
    updateString(digest, reason);
    return HexFormat.of().formatHex(digest.digest());
  }

  public static String idempotencyKey(IdempotencyKey key) {
    MessageDigest digest = newDigest();
    updateString(digest, IDEMPOTENCY_DOMAIN);
    updateString(digest, key.value());
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String action(MarketStatus requestedStatus) {
    return switch (requestedStatus) {
      case SUSPENDED -> "suspend";
      case CLOSED -> "close";
      case OPEN -> "reopen";
    };
  }

  private static void updateString(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
