package com.sportsbook.betting.client;

import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class InternalClientHeaders implements ClientHttpRequestInterceptor {

  static final String SERVICE_HEADER = "X-Internal-Service";
  static final String KEY_HEADER = "X-Internal-Api-Key";
  static final String CALLER = "betting-service";

  private final String apiKey;

  InternalClientHeaders(String apiKey) {
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    HttpHeaders headers = request.getHeaders();
    headers.set(SERVICE_HEADER, CALLER);
    headers.set(KEY_HEADER, apiKey);
    return execution.execute(request, body);
  }
}
