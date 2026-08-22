package com.sportsbook.settlement.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class AdminRequestFingerprint {

  private AdminRequestFingerprint() {}

  public static String create(AdminAction.Kind kind, UUID targetId, String canonicalPayload) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(targetId, "targetId");
    Objects.requireNonNull(canonicalPayload, "canonicalPayload");
    String canonical = "admin-command-v1\n" + kind + "\n" + targetId + "\n" + canonicalPayload;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
