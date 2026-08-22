package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OddsRestClientIsolationTest {

  @Test
  void sendsOnlyTheOddsCredentialToTheOddsOrigin() throws Exception {
    AtomicReference<Map<String, List<String>>> received = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/probe",
        exchange -> {
          received.set(
              exchange.getRequestHeaders().entrySet().stream()
                  .collect(
                      Collectors.toUnmodifiableMap(
                          entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue)));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    try {
      DownstreamProperties defaults = ClientIsolationFixture.properties();
      DownstreamProperties properties =
          new DownstreamProperties(
              defaults.walletBaseUrl(),
              defaults.riskBaseUrl(),
              URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
              defaults.settlementBaseUrl(),
              defaults.connectTimeout(),
              defaults.readTimeout());
      RestClient client =
          new DownstreamClientConfiguration()
              .oddsRestClient(
                  RestClient.builder(), properties, ClientIsolationFixture.credentials());

      client.get().uri("/probe").retrieve().toBodilessEntity();

      assertThat(received.get().get(DownstreamHeaders.INTERNAL_SERVICE.toLowerCase(Locale.ROOT)))
          .containsExactly(DownstreamHeaders.ADMIN_API);
      assertThat(received.get().get(DownstreamHeaders.INTERNAL_API_KEY.toLowerCase(Locale.ROOT)))
          .containsExactly(ClientIsolationFixture.ODDS);
      assertThat(received.get())
          .doesNotContainKeys(
              DownstreamHeaders.SERVICE_NAME.toLowerCase(Locale.ROOT),
              DownstreamHeaders.API_KEY.toLowerCase(Locale.ROOT));
      assertThat(received.get().values())
          .allSatisfy(
              values ->
                  assertThat(values)
                      .doesNotContain(
                          ClientIsolationFixture.WALLET,
                          ClientIsolationFixture.RISK,
                          ClientIsolationFixture.SETTLEMENT));
    } finally {
      server.stop(0);
    }
  }
}
