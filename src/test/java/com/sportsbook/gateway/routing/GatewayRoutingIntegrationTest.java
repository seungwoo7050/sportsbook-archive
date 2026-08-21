package com.sportsbook.gateway.routing;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

  static final WireMockServer DOWNSTREAM = startDownstream();
  private static final KeyPair KEYS = keyPair();

  @Autowired TestRestTemplate http;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gateway.security.jwt.public-key", GatewayRoutingIntegrationTest::publicKey);
    registry.add("gateway.ratelimit.enabled", () -> "false");
    registry.add("gateway.downstream.betting-uri", DOWNSTREAM::baseUrl);
  }

  @BeforeEach
  void resetDownstream() {
    DOWNSTREAM.resetAll();
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.stop();
  }

  HttpHeaders authenticated(UUID subject) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject.toString())
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("roles", List.of("USER"))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwt.serialize());
    return headers;
  }

  private static WireMockServer startDownstream() {
    WireMockServer server =
        new WireMockServer(
            wireMockConfig().dynamicPort().http2PlainDisabled(true).http2TlsDisabled(true));
    server.start();
    return server;
  }

  private static KeyPair keyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }

  private static String publicKey() {
    String encoded = Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
  }
}
