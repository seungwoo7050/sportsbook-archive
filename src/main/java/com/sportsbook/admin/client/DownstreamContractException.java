package com.sportsbook.admin.client;

public final class DownstreamContractException extends RuntimeException {

  DownstreamContractException(String contract) {
    super("Downstream success response violated contract: " + contract);
  }

  DownstreamContractException(String contract, Throwable cause) {
    super("Downstream success response violated contract: " + contract, cause);
  }
}
