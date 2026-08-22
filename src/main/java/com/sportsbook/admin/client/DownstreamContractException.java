package com.sportsbook.admin.client;

public final class DownstreamContractException extends RuntimeException {

  DownstreamContractException(String contract) {
    super("Downstream success response violated contract: " + contract);
  }
}
