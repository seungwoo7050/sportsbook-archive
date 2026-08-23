package com.sportsbook.admin.client;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

final class CrossClientIntegrationFixture implements AutoCloseable {

  private final Map<String, Map<String, List<String>>> captured = new ConcurrentHashMap<>();
  private final HttpServer server;
  private final AnnotationConfigApplicationContext context;

  CrossClientIntegrationFixture() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          captured.put(exchange.getRequestURI().getPath(), lowerCase(exchange.getRequestHeaders()));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
    context = context();
  }

  void invoke(String beanName, String path) {
    context.getBean(beanName, RestClient.class).get().uri(path).retrieve().toBodilessEntity();
  }

  Map<String, List<String>> headers(String path) {
    return captured.get(path);
  }

  @Override
  public void close() {
    context.close();
    server.stop(0);
  }

  private AnnotationConfigApplicationContext context() {
    URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    DownstreamProperties defaults = ClientIsolationFixture.properties();
    DownstreamProperties properties =
        new DownstreamProperties(
            origin, origin, origin, origin, defaults.connectTimeout(), defaults.readTimeout());
    AnnotationConfigApplicationContext created = new AnnotationConfigApplicationContext();
    created.registerBean(RestClient.Builder.class, () -> RestClient.builder());
    created.registerBean(DownstreamProperties.class, () -> properties);
    created.registerBean(DownstreamCredentials.class, ClientIsolationFixture::credentials);
    created.register(DownstreamClientConfiguration.class);
    created.refresh();
    return created;
  }

  private static Map<String, List<String>> lowerCase(Map<String, List<String>> headers) {
    return headers.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
  }
}
