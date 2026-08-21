package com.sportsbook.gateway.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.jayway.jsonpath.JsonPath.read;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  static final String WALLET_KEY = "fixture-wallet-key-32-characters-long";
  static final WireMockServer DOWNSTREAM = startDownstream();
  private static final KeyPair KEYS = keyPair();

  @Autowired TestRestTemplate http;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gateway.security.jwt.public-key", GatewayRoutingIntegrationTest::publicKey);
    registry.add("gateway.ratelimit.enabled", () -> "false");
    registry.add("gateway.downstream.betting-uri", DOWNSTREAM::baseUrl);
    registry.add("gateway.downstream.wallet.uri", DOWNSTREAM::baseUrl);
    registry.add("gateway.downstream.wallet.api-key", () -> WALLET_KEY);
    registry.add("gateway.downstream.odds-feed-uri", DOWNSTREAM::baseUrl);
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
  void injectsWalletCredentialForTheVerifiedSubject() throws Exception {
    UUID subject = UUID.randomUUID();
    String path = "/internal/v1/wallet/accounts/" + subject + "/balance";
    DOWNSTREAM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)));
    HttpHeaders headers = authenticated(subject);
    headers.set("X-Internal-Service", "attacker");
    headers.set("X-Internal-Api-Key", "attacker-key");

    ResponseEntity<String> response =
        http.exchange(
            "/api/v1/wallet/balance", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    DOWNSTREAM.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withHeader("X-User-Id", equalTo(subject.toString()))
            .withHeader("X-Internal-Service", equalTo("gateway"))
            .withHeader("X-Internal-Api-Key", equalTo(WALLET_KEY))
            .withoutHeader(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void rejectsAnonymousAndUnexpectedWalletRequests() throws Exception {
    assertThat(http.getForEntity("/api/v1/wallet/balance", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            http.exchange(
                    "/api/v1/wallet/balance",
                    HttpMethod.POST,
                    new HttpEntity<>(authenticated(UUID.randomUUID())),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
  }

  @Test
  void proxiesExactPublicReadsWithoutForwardingCredentials() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    List<String> paths =
        List.of(
            "/api/v1/events",
            "/api/v1/events/" + eventId,
            "/api/v1/odds/" + eventId + "/" + marketId + "/" + selectionId);
    for (String path : paths) {
      DOWNSTREAM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)));
    }
    HttpHeaders headers = authenticated(UUID.randomUUID());
    headers.set("X-User-Id", "attacker");
    headers.set("X-Internal-Service", "attacker");
    headers.set("X-Internal-Api-Key", "attacker-key");

    for (String path : paths) {
      HttpEntity<?> request =
          path.equals("/api/v1/events") ? HttpEntity.EMPTY : new HttpEntity<>(headers);
      assertThat(http.exchange(path, HttpMethod.GET, request, String.class).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      DOWNSTREAM.verify(
          getRequestedFor(urlPathEqualTo(path))
              .withoutHeader(HttpHeaders.AUTHORIZATION)
              .withoutHeader("X-User-Id")
              .withoutHeader("X-Internal-Service")
              .withoutHeader("X-Internal-Api-Key"));
    }
  }

  @Test
  void rejectsUnexpectedPublicMethodsAndPathShapes() throws Exception {
    HttpEntity<Void> request = new HttpEntity<>(authenticated(UUID.randomUUID()));
    List<String> paths =
        List.of(
            "/api/v1/events/one/extra",
            "/api/v1/odds/event/market",
            "/api/v1/odds/event/market/selection/extra");
    assertThat(
            http.exchange("/api/v1/events", HttpMethod.POST, request, String.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    for (String path : paths) {
      assertThat(http.exchange(path, HttpMethod.GET, request, String.class).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(DOWNSTREAM.getAllServeEvents()).isEmpty();
  }

  @Test
  void rejectsUnsafeOddsFeedBaseUris() {
    for (String uri :
        List.of(
            "ftp://odds.internal",
            "http://user@odds.internal",
            "http://odds.internal/base",
            "http://odds.internal?query=1")) {
      assertThatThrownBy(() -> new OddsFeedDownstreamProperties(URI.create(uri)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void preservesDownstreamStatusHeadersAndBody() {
    String problem = "{\"errorCode\":\"ODDS_MARKET_CLOSED\",\"detail\":\"closed\"}";
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/api/v1/events/problem"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .withHeader(HttpHeaders.LOCATION, "/api/v1/events/next")
                    .withHeader(HttpHeaders.RETRY_AFTER, "7")
                    .withBody(problem)));

    ResponseEntity<String> response = http.getForEntity("/api/v1/events/problem", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/events/next");
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
    assertThat(response.getBody()).isEqualTo(problem);
  }

  @Test
  void mapsPublicProxyTimeoutWithoutReauthentication() {
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/api/v1/events/timeout"))
            .willReturn(aResponse().withFixedDelay(4_000).withStatus(200)));

    ResponseEntity<String> timeout = http.getForEntity("/api/v1/events/timeout", String.class);

    assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(timeout.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(read(timeout.getBody(), "$.status").toString()).isEqualTo("504");
    assertThat(read(timeout.getBody(), "$.errorCode").toString()).isEqualTo("GATEWAY_TIMEOUT");
  }

  @Test
  void isolatesConcurrentIdentityIdempotencyAndTraceTuples() throws Exception {
    int clients = 24;
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/internal/v1/bets"))
            .willReturn(aResponse().withStatus(201).withBody("{\"accepted\":true}")));
    ExecutorService pool = Executors.newFixedThreadPool(clients);
    CyclicBarrier start = new CyclicBarrier(clients);
    List<Future<ResponseEntity<String>>> responses = new ArrayList<>();
    try {
      for (int index = 0; index < clients; index++) {
        int request = index;
        responses.add(
            pool.submit(
                () -> {
                  start.await(5, TimeUnit.SECONDS);
                  HttpHeaders headers = authenticated(new UUID(0, request + 1));
                  headers.setContentType(MediaType.APPLICATION_JSON);
                  headers.set("Idempotency-Key", "fixture-" + request);
                  headers.set("traceparent", traceparent(request));
                  headers.set("X-User-Id", new UUID(-1, request + 1).toString());
                  headers.set("X-User-Roles", "ADMIN");
                  headers.set("X-Internal-Service", "attacker");
                  headers.set("X-Internal-Api-Key", "attacker-key");
                  return http.exchange(
                      "/api/v1/bets",
                      HttpMethod.POST,
                      new HttpEntity<>(requestBody(request), headers),
                      String.class);
                }));
      }
      for (Future<ResponseEntity<String>> response : responses) {
        assertThat(response.get(30, TimeUnit.SECONDS).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
      }
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    DOWNSTREAM.verify(clients, postRequestedFor(urlPathEqualTo("/internal/v1/bets")));
    for (int index = 0; index < clients; index++) {
      DOWNSTREAM.verify(
          1,
          postRequestedFor(urlPathEqualTo("/internal/v1/bets"))
              .withHeader("X-User-Id", equalTo(new UUID(0, index + 1).toString()))
              .withHeader("X-User-Roles", equalTo("USER"))
              .withHeader("Idempotency-Key", equalTo("fixture-" + index))
              .withHeader("traceparent", equalTo(traceparent(index)))
              .withoutHeader(HttpHeaders.AUTHORIZATION)
              .withoutHeader("X-Internal-Service")
              .withoutHeader("X-Internal-Api-Key")
              .withRequestBody(equalTo(requestBody(index))));
    }
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

  private static String requestBody(int index) {
    return "{\"request\":" + index + "}";
  }

  private static String traceparent(int index) {
    return String.format("00-%032x-%016x-01", index + 1, index + 1);
  }
}
