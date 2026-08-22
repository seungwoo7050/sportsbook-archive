package com.sportsbook.admin.client;

import org.springframework.http.HttpStatusCode;

public final class DownstreamUnavailableException extends RuntimeException {

  public enum Reason {
    SERVER_ERROR,
    TIMEOUT,
    TRANSPORT
  }

  private final Reason reason;
  private final HttpStatusCode status;

  DownstreamUnavailableException(Reason reason, HttpStatusCode status, Throwable cause) {
    super("Downstream outcome is unknown: " + reason, cause);
    this.reason = reason;
    this.status = status;
  }

  public Reason reason() {
    return reason;
  }

  public HttpStatusCode status() {
    return status;
  }
}
