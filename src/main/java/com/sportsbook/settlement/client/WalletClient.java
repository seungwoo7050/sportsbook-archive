package com.sportsbook.settlement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {

  static final String CREDIT_PATH = "/internal/v1/wallet/transactions/credit";
  static final String FORFEIT_PATH = "/internal/v1/wallet/transactions/forfeit";
  static final String ADJUSTMENT_PATH = "/internal/v1/wallet/transactions/adjustment";
  static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private final RestClient http;

  WalletClient(
      RestClient.Builder builder,
      WalletEndpointProperties endpoint,
      WalletAuthenticationHeaders authentication) {
    this(builder, endpoint, authentication, null);
  }

  @Autowired
  public WalletClient(
      RestClient.Builder builder,
      WalletEndpointProperties endpoint,
      WalletAuthenticationHeaders authentication,
      @Qualifier(WalletHttpConfiguration.REQUEST_FACTORY) ClientHttpRequestFactory requestFactory) {
    RestClient.Builder configured = builder.clone();
    if (requestFactory != null) {
      configured.requestFactory(requestFactory);
    }
    this.http =
        configured
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
            .onStatus(
                HttpStatusCode::isError,
                (request, httpResponse) -> WalletFailurePolicy.throwFor(httpResponse))
            .body(CreditResponse.class);
    return requireOperationGroupId(response);
  }

  public UUID forfeit(String idempotencyKey, UUID userId, Money amount) {
    CreditResponse response =
        http.post()
            .uri(FORFEIT_PATH)
            .header(IDEMPOTENCY_HEADER, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ForfeitRequest(userId, amount))
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, httpResponse) -> WalletFailurePolicy.throwFor(httpResponse))
            .body(CreditResponse.class);
    return requireOperationGroupId(response);
  }

  public WalletAdjustmentProof adjust(
      UUID revisionId,
      UUID betId,
      long revisionNumber,
      UUID userId,
      Money previousPayout,
      Money newPayout) {
    WalletAdjustmentProof proof =
        http.post()
            .uri(ADJUSTMENT_PATH)
            .header(IDEMPOTENCY_HEADER, "settlement:revision:" + revisionId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new AdjustmentRequest(
                    revisionId, betId, revisionNumber, userId, previousPayout, newPayout))
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, httpResponse) -> WalletFailurePolicy.throwFor(httpResponse))
            .body(WalletAdjustmentProof.class);
    if (proof == null || proof.revisionId() == null || proof.status() == null) {
      throw WalletFailurePolicy.malformedSuccess();
    }
    return proof;
  }

  public WalletAdjustmentProof findAdjustment(UUID revisionId) {
    WalletAdjustmentProof proof =
        http.get()
            .uri(ADJUSTMENT_PATH + "/{revisionId}", revisionId)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, httpResponse) -> WalletFailurePolicy.throwFor(httpResponse))
            .body(WalletAdjustmentProof.class);
    if (proof == null || !revisionId.equals(proof.revisionId()) || proof.status() == null) {
      throw WalletFailurePolicy.malformedSuccess();
    }
    return proof;
  }

  private static UUID requireOperationGroupId(CreditResponse response) {
    if (response == null || response.operationGroupId() == null) {
      throw WalletFailurePolicy.malformedSuccess();
    }
    return response.operationGroupId();
  }

  private record CreditRequest(UUID userId, Money amount, String source, String reason) {}

  private record ForfeitRequest(UUID userId, Money amount) {}

  private record AdjustmentRequest(
      UUID revisionId,
      UUID betId,
      long revisionNumber,
      UUID userId,
      Money previousPayout,
      Money newPayout) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CreditResponse(UUID operationGroupId) {}
}
