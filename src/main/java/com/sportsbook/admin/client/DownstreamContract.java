package com.sportsbook.admin.client;

import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class DownstreamContract {

  private DownstreamContract() {}

  public static <T> T requireBody(
      ResponseEntity<T> response, HttpStatus expectedStatus, Predicate<T> proof, String contract) {
    T body = response.getBody();
    if (response.getStatusCode().value() != expectedStatus.value()
        || body == null
        || !proof.test(body)) {
      throw new DownstreamContractException(contract);
    }
    return body;
  }

  public static void requireEmpty(
      ResponseEntity<byte[]> response, HttpStatus expectedStatus, String contract) {
    byte[] body = response.getBody();
    if (response.getStatusCode().value() != expectedStatus.value()
        || (body != null && body.length != 0)) {
      throw new DownstreamContractException(contract);
    }
  }
}
