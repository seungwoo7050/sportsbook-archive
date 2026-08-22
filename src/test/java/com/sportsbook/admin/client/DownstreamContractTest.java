package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DownstreamContractTest {

  @Test
  void acceptsOnlyTheExactStatusAndProvenResponseBody() {
    Receipt receipt = new Receipt("operation-1", true);

    assertThat(
            DownstreamContract.requireBody(
                ResponseEntity.ok(receipt), HttpStatus.OK, Receipt::valid, "wallet receipt"))
        .isSameAs(receipt);
    assertThatThrownBy(
            () ->
                DownstreamContract.requireBody(
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(receipt),
                    HttpStatus.OK,
                    Receipt::valid,
                    "wallet receipt"))
        .isInstanceOf(DownstreamContractException.class);
    assertThatThrownBy(
            () ->
                DownstreamContract.requireBody(
                    ResponseEntity.ok(new Receipt(null, true)),
                    HttpStatus.OK,
                    Receipt::valid,
                    "wallet receipt"))
        .isInstanceOf(DownstreamContractException.class);
    assertThatThrownBy(
            () ->
                DownstreamContract.requireBody(
                    ResponseEntity.ok().build(), HttpStatus.OK, Receipt::valid, "wallet receipt"))
        .isInstanceOf(DownstreamContractException.class);
  }

  @Test
  void requiresAnExactlyEmptyBodyForAcknowledgementResponses() {
    DownstreamContract.requireEmpty(
        ResponseEntity.status(HttpStatus.ACCEPTED).build(), HttpStatus.ACCEPTED, "odds ack");

    assertThatThrownBy(
            () ->
                DownstreamContract.requireEmpty(
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body("unexpected".getBytes(StandardCharsets.UTF_8)),
                    HttpStatus.ACCEPTED,
                    "odds ack"))
        .isInstanceOf(DownstreamContractException.class);
  }

  private record Receipt(String operationId, boolean accepted) {
    boolean valid() {
      return operationId != null && accepted;
    }
  }
}
