package com.sportsbook.admin.client;

import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

public final class DownstreamFailureMapper {

  public <T> T execute(Supplier<T> request) {
    try {
      return request.get();
    } catch (HttpClientErrorException rejection) {
      HttpHeaders headers = rejection.getResponseHeaders();
      MediaType contentType = headers == null ? null : headers.getContentType();
      throw new DownstreamStatusException(
          rejection.getStatusCode(), contentType, rejection.getResponseBodyAsByteArray());
    }
  }

  public void execute(Runnable request) {
    execute(
        () -> {
          request.run();
          return null;
        });
  }
}
