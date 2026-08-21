package com.sportsbook.gateway.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class RsaPublicKeyLoader {

  private static final String BEGIN = "-----BEGIN PUBLIC KEY-----";
  private static final String END = "-----END PUBLIC KEY-----";
  private static final int MINIMUM_RSA_BITS = 2048;

  RSAPublicKey load(String configuredKey) {
    if (configuredKey == null || configuredKey.isBlank()) {
      throw new IllegalStateException("GATEWAY_JWT_PUBLIC_KEY is required");
    }

    String pem = configuredKey.replace("\\n", "\n").trim();
    if (!pem.startsWith(BEGIN) || !pem.endsWith(END)) {
      throw new IllegalStateException("GATEWAY_JWT_PUBLIC_KEY must contain an RSA public key");
    }

    try {
      String encoded =
          pem.substring(BEGIN.length(), pem.length() - END.length()).replaceAll("\\s+", "");
      byte[] der = Base64.getDecoder().decode(encoded);
      RSAPublicKey key =
          (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
      if (key.getModulus().bitLength() < MINIMUM_RSA_BITS) {
        throw new IllegalStateException("GATEWAY_JWT_PUBLIC_KEY must be at least 2048 bits");
      }
      return key;
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("GATEWAY_JWT_PUBLIC_KEY is malformed", exception);
    }
  }
}
