package com.sportsbook.admin.client;

import java.util.Arrays;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

public final class DownstreamStatusException extends RuntimeException {

  private final HttpStatusCode status;
  private final MediaType contentType;
  private final byte[] body;

  DownstreamStatusException(HttpStatusCode status, MediaType contentType, byte[] body) {
    super("Downstream request was rejected with status " + status.value());
    this.status = status;
    this.contentType = contentType;
    this.body = Arrays.copyOf(body, body.length);
  }

  public HttpStatusCode status() {
    return status;
  }

  public MediaType contentType() {
    return contentType;
  }

  public byte[] body() {
    return Arrays.copyOf(body, body.length);
  }
}
