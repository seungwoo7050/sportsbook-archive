package com.sportsbook.admin.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class RsaPublicKeyParser {

  private static final String BEGIN = "-----BEGIN PUBLIC KEY-----";
  private static final String END = "-----END PUBLIC KEY-----";
  private static final int MINIMUM_RSA_BITS = 2048;

  RSAPublicKey parse(String configuredKey) {
    if (configuredKey == null || configuredKey.isBlank()) {
      throw new IllegalStateException("ADMIN_JWT_PUBLIC_KEY is required");
    }

    String pem = configuredKey.replace("\\n", "\n").trim();
    if (!pem.startsWith(BEGIN) || !pem.endsWith(END)) {
      throw new IllegalStateException("ADMIN_JWT_PUBLIC_KEY must be an SPKI public key");
    }

    try {
      String encoded =
          pem.substring(BEGIN.length(), pem.length() - END.length()).replaceAll("\\s+", "");
      byte[] der = Base64.getDecoder().decode(encoded);
      var parsed = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
      if (!(parsed instanceof RSAPublicKey rsaKey)) {
        throw new IllegalStateException("ADMIN_JWT_PUBLIC_KEY must be RSA");
      }
      if (rsaKey.getModulus().bitLength() < MINIMUM_RSA_BITS) {
        throw new IllegalStateException("ADMIN_JWT_PUBLIC_KEY must be at least 2048 bits");
      }
      return rsaKey;
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("ADMIN_JWT_PUBLIC_KEY is malformed", exception);
    }
  }
}
