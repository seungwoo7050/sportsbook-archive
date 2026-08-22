package com.sportsbook.betting.error;

import com.sportsbook.protocol.error.ErrorCode;

public class WalletRejectedException extends BetPlacementException {

  private final String walletErrorCode;

  public WalletRejectedException(String walletErrorCode, String detail) {
    super(ErrorCode.VALIDATION_FAILED, detail);
    this.walletErrorCode = walletErrorCode;
  }

  public String walletErrorCode() {
    return walletErrorCode;
  }
}
