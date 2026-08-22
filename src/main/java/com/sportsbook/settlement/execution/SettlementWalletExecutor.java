package com.sportsbook.settlement.execution;

import com.sportsbook.settlement.client.WalletClient;
import com.sportsbook.settlement.client.WalletCreditPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SettlementWalletExecutor {

  private final WalletClient wallet;

  public SettlementWalletExecutor(WalletClient wallet) {
    this.wallet = wallet;
  }

  public Optional<UUID> releaseLocked(SettlementAttempt attempt, UUID userId) {
    if (attempt.money().lockedRelease().isZero()) {
      return Optional.empty();
    }
    boolean wholeSlipVoid = attempt.action() == SettlementAttempt.Action.VOID;
    String key = (wholeSlipVoid ? "void:refund:" : "settle:refund:") + attempt.betId();
    WalletCreditPurpose purpose =
        wholeSlipVoid ? WalletCreditPurpose.WHOLE_SLIP_VOID : WalletCreditPurpose.RETURNED_STAKE;
    return Optional.of(wallet.credit(key, userId, attempt.money().lockedRelease(), purpose));
  }

  public Optional<UUID> forfeitLocked(SettlementAttempt attempt, UUID userId) {
    if (attempt.money().lockedForfeit().isZero()) {
      return Optional.empty();
    }
    return Optional.of(
        wallet.forfeit(
            "settle:forfeit:" + attempt.betId(), userId, attempt.money().lockedForfeit()));
  }
}
