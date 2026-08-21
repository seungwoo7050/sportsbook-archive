package com.sportsbook.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtVerificationTest {

  private KeyPair trusted;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() throws Exception {
    trusted = keyPair();
    decoder = new JwtDecoderConfiguration().jwtDecoder(new JwtSecurityProperties(pem(trusted)));
  }

  @Test
  void requiresWellFormedPublicKey() {
    RsaPublicKeyLoader loader = new RsaPublicKeyLoader();
    assertThatThrownBy(() -> loader.load(" ")).isInstanceOf(IllegalStateException.class);
    String corrupted = pem(trusted).replaceFirst("\n", "\n!@#");
    assertThatThrownBy(() -> loader.load(corrupted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(corrupted);
  }

  @Test
  void acceptsValidRs256Token() throws Exception {
    assertThat(decoder.decode(sign(trusted, claims(future()))).getSubject()).isNotBlank();
  }

  @Test
  void rejectsWrongSigningKey() throws Exception {
    String token = sign(keyPair(), claims(future()));
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void requiresUnexpiredLifetime() throws Exception {
    String expired = sign(trusted, claims(Date.from(Instant.now().minusSeconds(1))));
    String missing = sign(trusted, claims(null));
    assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder.decode(missing)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsUnsignedAndHmacTokens() throws Exception {
    String unsigned = new PlainJWT(claims(future())).serialize();
    SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(future()));
    hmac.sign(new MACSigner(new byte[32]));
    assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> decoder.decode(hmac.serialize())).isInstanceOf(JwtException.class);
  }

  private static JWTClaimsSet claims(Date expiry) {
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString());
    return expiry == null ? builder.build() : builder.expirationTime(expiry).build();
  }

  private static String sign(KeyPair pair, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
    return jwt.serialize();
  }

  private static KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static Date future() {
    return Date.from(Instant.now().plusSeconds(300));
  }

  private static String pem(KeyPair pair) {
    String body = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
  }
}
