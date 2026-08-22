package com.sportsbook.settlement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {

  static final String CREDIT_PATH = "/internal/v1/wallet/transactions/credit";
  static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private final RestClient http;

  public WalletClient(
      RestClient.Builder builder,
      WalletEndpointProperties endpoint,
      WalletAuthenticationHeaders authentication) {
    this.http =
        builder
            .clone()
            .baseUrl(endpoint.baseUrl().toString())
            .defaultHeaders(authentication::apply)
            .build();
  }

  public UUID credit(
      String idempotencyKey, UUID userId, Money amount, WalletCreditPurpose purpose) {
    CreditResponse response =
        http.post()
            .uri(CREDIT_PATH)
            .header(IDEMPOTENCY_HEADER, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreditRequest(userId, amount, purpose.source(), purpose.reason()))
            .retrieve()
            .body(CreditResponse.class);
    return Objects.requireNonNull(response, "wallet credit response").operationGroupId();
  }

  private record CreditRequest(UUID userId, Money amount, String source, String reason) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CreditResponse(UUID operationGroupId) {}
}
