package com.sportsbook.admin.client;

import java.util.Objects;
import java.util.UUID;

public final class WalletOperationProof {

  private static final String REFUND_LEDGER_REASON = "BET_REFUND";

  private WalletOperationProof() {}

  public static UUID verifyRefund(WalletCreditPayload request, WalletOperationResponse response) {
    Objects.requireNonNull(request, "request");
    if (response == null
        || response.operationGroupId() == null
        || !request.userId().equals(response.userId())
        || !request.amount().equals(response.amount())
        || !REFUND_LEDGER_REASON.equals(response.reason())
        || response.at() == null) {
      throw new DownstreamContractException("complete matching Wallet refund proof");
    }
    return response.operationGroupId();
  }
}
