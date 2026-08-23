package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WalletClientExactRequestTest {

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void sendsTheRawKeyAndExactRefundRequest() throws Exception {
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000105");
    UUID operationGroup = UUID.fromString("018f0000-0000-7000-8000-000000000106");
    AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/internal/v1/wallet/transactions/credit",
        exchange -> {
          captured.set(
              new CapturedRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().toString(),
                  exchange.getRequestHeaders().getFirst(DownstreamHeaders.INTERNAL_SERVICE),
                  exchange.getRequestHeaders().getFirst(DownstreamHeaders.INTERNAL_API_KEY),
                  exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                  exchange.getRequestBody().readAllBytes()));
          byte[] response =
              ("{\"operationGroupId\":\""
                      + operationGroup
                      + "\",\"userId\":\""
                      + userId
                      + "\",\"amount\":{\"amount\":750,\"currency\":\"KRW\"},"
                      + "\"reason\":\"BET_REFUND\",\"at\":\"2026-08-22T01:02:03Z\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();

    try {
      RestClient http = walletRestClient(server);

      UUID result =
          new WalletClient(http).refund(userId, Money.krw(750), IdempotencyKey.of("refund key 01"));

      assertThat(result).isEqualTo(operationGroup);
      assertThat(captured.get().method()).isEqualTo("POST");
      assertThat(captured.get().path()).isEqualTo("/internal/v1/wallet/transactions/credit");
      assertThat(captured.get().service()).isEqualTo("admin-api");
      assertThat(captured.get().apiKey()).isEqualTo(ClientIsolationFixture.WALLET);
      assertThat(captured.get().idempotencyKey()).isEqualTo("refund key 01");
      JsonNode body = json.readTree(captured.get().body());
      assertThat(body)
          .isEqualTo(
              json.readTree(
                  """
                  {"userId":"018f0000-0000-7000-8000-000000000105",
                   "amount":{"amount":750,"currency":"KRW"},
                   "source":"HOUSE_POOL","reason":"REFUND"}
                  """));
    } finally {
      server.stop(0);
    }
  }

  private static RestClient walletRestClient(HttpServer server) {
    DownstreamProperties defaults = ClientIsolationFixture.properties();
    var properties =
        new DownstreamProperties(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            defaults.riskBaseUrl(),
            defaults.oddsFeedBaseUrl(),
            defaults.settlementBaseUrl(),
            defaults.connectTimeout(),
            defaults.readTimeout());
    return new DownstreamClientConfiguration()
        .walletRestClient(RestClient.builder(), properties, ClientIsolationFixture.credentials());
  }

  private record CapturedRequest(
      String method,
      String path,
      String service,
      String apiKey,
      String idempotencyKey,
      byte[] body) {}
}
