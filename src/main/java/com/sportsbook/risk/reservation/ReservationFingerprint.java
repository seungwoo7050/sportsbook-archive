package com.sportsbook.risk.reservation;

import com.sportsbook.risk.service.RiskCheckCommand;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Versioned canonical request binding exposed as an opaque reservation token. */
public final class ReservationFingerprint {
  private static final String VERSION = "risk-reservation-v1";

  private ReservationFingerprint() {}

  public static String of(RiskCheckCommand command) {
    Objects.requireNonNull(command, "command");
    MessageDigest digest = sha256();
    add(digest, VERSION);
    add(digest, command.userId().value().toString());
    add(digest, command.betId().value().toString());
    add(digest, Long.toString(command.stake().amount()));
    add(digest, command.stake().currency().name());
    command.selectionIds().stream()
        .map(selection -> selection.value().toString())
        .sorted()
        .forEach(value -> add(digest, value));
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void add(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
