package com.sportsbook.betting.error;

import java.util.Objects;

public final class WalletProofMismatchException extends DependencyUnavailableException {

  private final String operation;

  public WalletProofMismatchException(String operation) {
    super("Wallet returned a mismatched " + operation + " proof");
    this.operation = Objects.requireNonNull(operation, "operation");
  }

  public String operation() {
    return operation;
  }
}
