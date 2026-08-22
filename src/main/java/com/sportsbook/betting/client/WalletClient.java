package com.sportsbook.betting.client;

import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.WalletProofMismatchException;
import com.sportsbook.betting.error.WalletRejectedException;
import com.sportsbook.protocol.value.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WalletClient {

  private static final String DEBIT = "/internal/v1/wallet/transactions/debit";
  private static final String DEBIT_REASON = "BET_DEBIT";
  private static final String REFUND_REASON = "BET_REFUND";

  private final RestClient http;
  private final WalletProblemMapper problems;

  public WalletClient(
      @Qualifier("walletRestClient") RestClient http, WalletProblemMapper problems) {
    this.http = http;
    this.problems = problems;
  }

  public UUID debit(UUID betId, UUID userId, Money fullExposure) {
    try {
      WalletOperationResponse response =
          http.post()
              .uri(DEBIT)
              .header("Idempotency-Key", betId.toString())
              .contentType(MediaType.APPLICATION_JSON)
              .body(new WalletDebitRequest(userId, fullExposure))
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (request, error) -> {
                    throw problems.map(problems.read(error));
                  })
              .body(WalletOperationResponse.class);
      return requireDebitProof(response, userId, fullExposure);
    } catch (BetPlacementException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new DependencyUnavailableException("Wallet debit is unavailable", exception);
    }
  }

  public Optional<WalletOperationResponse> findDebit(UUID betId, UUID userId, Money fullExposure) {
    try {
      WalletOperationResponse response =
          http.get()
              .uri(DEBIT + "/{betId}", betId)
              .retrieve()
              .onStatus(
                  status -> status.value() == HttpStatus.NOT_FOUND.value(),
                  (request, error) -> {
                    WalletProblem problem = problems.read(error);
                    if (WalletProblemMapper.OPERATION_NOT_FOUND.equals(problem.errorCode())) {
                      throw new DebitAbsent();
                    }
                    throw problems.map(problem);
                  })
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (request, error) -> {
                    throw problems.map(problems.read(error));
                  })
              .body(WalletOperationResponse.class);
      requireDebitProof(response, userId, fullExposure);
      return Optional.of(response);
    } catch (DebitAbsent exception) {
      return Optional.empty();
    } catch (BetPlacementException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new DependencyUnavailableException("Wallet debit lookup is unavailable", exception);
    }
  }

  private static UUID requireOperationId(WalletOperationResponse response) {
    if (response == null || response.operationGroupId() == null) {
      throw new DependencyUnavailableException("Wallet returned no operationGroupId");
    }
    return response.operationGroupId();
  }

  private static UUID requireProof(
      WalletOperationResponse response, UUID userId, Money amount, String reason) {
    if (response == null
        || response.operationGroupId() == null
        || !userId.equals(response.userId())
        || !amount.equals(response.amount())
        || !reason.equals(response.reason())
        || response.at() == null) {
      String operation = reason.equals(REFUND_REASON) ? "refund" : "debit";
      throw new WalletProofMismatchException(operation);
    }
    return response.operationGroupId();
  }

  private static UUID requireDebitProof(
      WalletOperationResponse response, UUID userId, Money amount) {
    try {
      return requireProof(response, userId, amount, DEBIT_REASON);
    } catch (WalletProofMismatchException mismatch) {
      throw new WalletRejectedException(
          "WALLET_OPERATION_MISMATCH", "Wallet debit proof did not match this bet");
    }
  }

  private static final class DebitAbsent extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
