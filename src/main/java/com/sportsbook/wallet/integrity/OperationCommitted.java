package com.sportsbook.wallet.integrity;

import java.util.Objects;
import java.util.UUID;

/** Transaction-bound notification used by post-commit integrity checks. */
public record OperationCommitted(UUID operationGroupId) {

  public OperationCommitted {
    Objects.requireNonNull(operationGroupId, "operationGroupId");
  }
}
