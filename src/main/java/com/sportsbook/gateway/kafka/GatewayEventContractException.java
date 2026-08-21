package com.sportsbook.gateway.kafka;

/** A permanent event contract violation that must bypass transient delivery retries. */
public final class GatewayEventContractException extends RuntimeException {

  public GatewayEventContractException(String message) {
    super(message);
  }

  public GatewayEventContractException(String message, Throwable cause) {
    super(message, cause);
  }
}
