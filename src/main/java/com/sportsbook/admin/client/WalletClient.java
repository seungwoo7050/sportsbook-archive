package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {

  private final RestClient http;
  private final DownstreamFailureMapper failures = new DownstreamFailureMapper();

  public WalletClient(@Qualifier("walletRestClient") RestClient http) {
    this.http = http;
  }

  public UUID refund(UUID userId, Money amount, IdempotencyKey idempotencyKey) {
    WalletCreditPayload request = WalletCreditPayload.refund(userId, amount);
    var response =
        failures.execute(
            () ->
                http.post()
                    .uri("/internal/v1/wallet/transactions/credit")
                    .header("Idempotency-Key", idempotencyKey.value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(WalletOperationResponse.class));
    WalletOperationResponse proof =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Wallet refund must return HTTP 200 with a body");
    return WalletOperationProof.verifyRefund(request, proof);
  }
}
