package com.sportsbook.admin.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public final class TestJwtKeys {

  private static final KeyPair TRUSTED = generate();

  private TestJwtKeys() {}

  public static String publicKeyPem() {
    String body = Base64.getEncoder().encodeToString(TRUSTED.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
  }

  public static String bearer(String subject, String role) {
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(subject)
              .claim("role", role)
              .issueTime(Date.from(now))
              .notBeforeTime(Date.from(now.minusSeconds(1)))
              .expirationTime(Date.from(now.plusSeconds(300)))
              .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner((RSAPrivateKey) TRUSTED.getPrivate()));
      return "Bearer " + jwt.serialize();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not sign test JWT", exception);
    }
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not generate test key", exception);
    }
  }
}
