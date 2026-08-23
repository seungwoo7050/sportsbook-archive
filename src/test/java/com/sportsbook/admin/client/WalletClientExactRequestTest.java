package com.sportsbook.admin.client;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import org.springframework.web.client.RestClient;

class WalletClientExactRequestTest {

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
