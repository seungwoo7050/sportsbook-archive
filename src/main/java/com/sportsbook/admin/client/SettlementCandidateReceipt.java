package com.sportsbook.admin.client;

import java.util.UUID;

public record SettlementCandidateReceipt(UUID idempotencyKey, Outcome outcome, Boolean replay) {

  public static SettlementCandidateReceipt verify(
      UUID requestedKey, Outcome expectedOutcome, SettlementCandidateReceipt receipt) {
    if (receipt == null
        || !requestedKey.equals(receipt.idempotencyKey())
        || receipt.outcome() != expectedOutcome
        || receipt.replay() == null) {
      throw new DownstreamContractException("matching Settlement candidate receipt");
    }
    return receipt;
  }

  public enum Outcome {
    CANDIDATE_APPROVED,
    CANDIDATE_REJECTED
  }
}
