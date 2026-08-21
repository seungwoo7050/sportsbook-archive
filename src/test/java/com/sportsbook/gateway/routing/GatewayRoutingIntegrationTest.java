package com.sportsbook.gateway.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @Test
  void forwardsBetPlacementBodyAndVerifiedIdentity() throws Exception {
    UUID subject = UUID.randomUUID();
    String body = "{\"stake\":\"10.00\"}";
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/internal/v1/bets"))
            .willReturn(aResponse().withStatus(201).withBody("{\"betId\":\"accepted\"}")));
    HttpHeaders headers = authenticated(subject);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", "fixture-idempotency-key");
    headers.set("X-User-Id", UUID.randomUUID().toString());
    headers.set("X-Internal-Service", "attacker");
    headers.set("X-Internal-Api-Key", "attacker-key");

    ResponseEntity<String> response =
        http.exchange(
            "/api/v1/bets", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DOWNSTREAM.verify(
        postRequestedFor(urlPathEqualTo("/internal/v1/bets"))
            .withHeader("X-User-Id", equalTo(subject.toString()))
            .withHeader("X-User-Roles", equalTo("USER"))
            .withHeader("Idempotency-Key", equalTo("fixture-idempotency-key"))
            .withoutHeader(HttpHeaders.AUTHORIZATION)
            .withoutHeader("X-Internal-Service")
            .withoutHeader("X-Internal-Api-Key")
            .withRequestBody(equalTo(body)));
  }

  @Test
  void scopesBetReadsToTheVerifiedSubject() throws Exception {
    UUID subject = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/internal/v1/bets")).willReturn(aResponse().withStatus(200)));
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/internal/v1/bets/" + betId)).willReturn(aResponse().withStatus(200)));

    http.exchange(
        "/api/v1/bets?userId=attacker&status=OPEN",
        HttpMethod.GET,
        new HttpEntity<>(authenticated(subject)),
        String.class);
    http.exchange(
        "/api/v1/bets/" + betId,
        HttpMethod.GET,
        new HttpEntity<>(authenticated(subject)),
        String.class);

    DOWNSTREAM.verify(
        getRequestedFor(urlPathEqualTo("/internal/v1/bets"))
            .withQueryParam("userId", equalTo(subject.toString()))
            .withQueryParam("status", equalTo("OPEN")));
    DOWNSTREAM.verify(getRequestedFor(urlPathEqualTo("/internal/v1/bets/" + betId)));
  }

  @Test
  void rejectsUnsafeBettingBaseUris() {
    assertThatThrownBy(() -> bettingUri("ftp://betting.internal"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> bettingUri("http://user@betting.internal"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> bettingUri("http://betting.internal/base"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> bettingUri("http://betting.internal?query=1"))
        .isInstanceOf(IllegalArgumentException.class);
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

  private static BettingDownstreamProperties bettingUri(String value) {
    return new BettingDownstreamProperties(URI.create(value));
  }
}
