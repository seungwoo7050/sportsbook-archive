package com.sportsbook.wallet.security;

import com.sportsbook.wallet.domain.WalletCaller;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Holds only credential digests and compares every presented key in constant time. */
public final class WalletCredentials {
  private static final byte[] UNKNOWN_CALLER_DIGEST = digest("wallet-unknown-caller");

  private final Map<WalletCaller, byte[]> callerDigests;

  public WalletCredentials(WalletSecurityProperties properties) {
    Map<WalletCaller, byte[]> digests = new EnumMap<>(WalletCaller.class);
    for (WalletCaller caller : WalletCaller.values()) {
      digests.put(caller, digest(properties.apiKey(caller)));
    }
    callerDigests = Map.copyOf(digests);
  }

  public Optional<WalletCaller> authenticate(String wireName, String apiKey) {
    Optional<WalletCaller> caller = WalletCaller.fromWireName(wireName);
    byte[] expected = caller.map(callerDigests::get).orElse(UNKNOWN_CALLER_DIGEST);
    byte[] presented = digest(apiKey == null ? "" : apiKey);
    boolean matches = MessageDigest.isEqual(expected, presented);
    return matches ? caller : Optional.empty();
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
