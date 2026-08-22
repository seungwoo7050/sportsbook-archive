package com.sportsbook.betting.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.InsufficientBalanceException;
import com.sportsbook.betting.error.WalletRejectedException;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
public class WalletProblemMapper {

  static final String OPERATION_NOT_FOUND = "WALLET_OPERATION_NOT_FOUND";
  static final String ACCOUNT_NOT_FOUND = "WALLET_ACCOUNT_NOT_FOUND";
  static final String INSUFFICIENT_BALANCE = "WALLET_INSUFFICIENT_BALANCE";
  static final String CURRENCY_MISMATCH = "WALLET_CURRENCY_MISMATCH";
  static final String AMOUNT_OUT_OF_RANGE = "WALLET_AMOUNT_OUT_OF_RANGE";
  static final String RECOVERY_BLOCKED = "WALLET_ACCOUNT_RECOVERY_BLOCKED";
  static final String IDEMPOTENCY_CONFLICT = "WALLET_IDEMPOTENCY_CONFLICT";

  private final ObjectMapper mapper;

  public WalletProblemMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  WalletProblem read(ClientHttpResponse response) {
    try {
      WalletProblem problem = mapper.readValue(response.getBody(), WalletProblem.class);
      if (problem.errorCode() == null || problem.errorCode().isBlank()) {
        throw new IOException("missing errorCode");
      }
      return problem;
    } catch (IOException exception) {
      throw new DependencyUnavailableException("Wallet returned an unreadable problem", exception);
    }
  }

  RuntimeException map(WalletProblem problem) {
    return switch (problem.errorCode()) {
      case INSUFFICIENT_BALANCE -> new InsufficientBalanceException(problem.detail());
      case ACCOUNT_NOT_FOUND,
              CURRENCY_MISMATCH,
              AMOUNT_OUT_OF_RANGE,
              RECOVERY_BLOCKED,
              IDEMPOTENCY_CONFLICT ->
          new WalletRejectedException(problem.errorCode(), problem.detail());
      default -> new DependencyUnavailableException("Wallet returned an unexpected problem");
    };
  }
}
