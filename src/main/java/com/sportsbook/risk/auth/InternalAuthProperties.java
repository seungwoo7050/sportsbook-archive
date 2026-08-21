package com.sportsbook.risk.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validates internal caller secrets and retains only constant-time comparable digests. */
@ConfigurationProperties(prefix = "risk.auth")
public final class InternalAuthProperties {
  public static final int MIN_SECRET_LENGTH = 32;

  private final Map<Caller, byte[]> digests;

  public InternalAuthProperties(
      String bettingServiceApiKey, String adminApiKey, String platformApiKey) {
    Map<Caller, String> secrets =
        Map.of(
            Caller.BETTING_SERVICE, requireSecret(bettingServiceApiKey, Caller.BETTING_SERVICE),
            Caller.ADMIN_API, requireSecret(adminApiKey, Caller.ADMIN_API),
            Caller.PLATFORM, requireSecret(platformApiKey, Caller.PLATFORM));
    if (new HashSet<>(secrets.values()).size() != Caller.values().length) {
      throw new IllegalArgumentException("internal caller secrets must be distinct");
    }
    EnumMap<Caller, byte[]> result = new EnumMap<>(Caller.class);
    secrets.forEach((caller, secret) -> result.put(caller, digest(secret)));
    digests = Map.copyOf(result);
  }

  public boolean matches(Caller caller, String candidate) {
    if (caller == null || candidate == null) {
      return false;
    }
    return MessageDigest.isEqual(digests.get(caller), digest(candidate));
  }

  private static String requireSecret(String secret, Caller caller) {
    if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          caller.wireName + " secret must contain at least 32 characters");
    }
    return secret;
  }

  private static byte[] digest(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public enum Caller {
    BETTING_SERVICE("betting-service"),
    ADMIN_API("admin-api"),
    PLATFORM("platform");

    private final String wireName;

    Caller(String wireName) {
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }

    public static Optional<Caller> fromWire(String value) {
      return java.util.Arrays.stream(values())
          .filter(caller -> caller.wireName.equals(value))
          .findFirst();
    }
  }
}
