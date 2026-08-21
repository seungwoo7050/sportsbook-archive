package com.sportsbook.wallet.domain.error;

import com.sportsbook.wallet.domain.WalletCaller;
import java.util.Objects;

/** Rejects an authenticated caller whose requested wallet capability is not allowed. */
public final class WalletAccessDeniedException extends RuntimeException {
  private final WalletCaller caller;
  private final String capability;

  public WalletAccessDeniedException(WalletCaller caller, String capability) {
    super(
        Objects.requireNonNull(caller, "caller").wireName()
            + " cannot perform "
            + Objects.requireNonNull(capability, "capability"));
    this.caller = caller;
    this.capability = capability;
  }

  public WalletCaller caller() {
    return caller;
  }

  public String capability() {
    return capability;
  }
}
