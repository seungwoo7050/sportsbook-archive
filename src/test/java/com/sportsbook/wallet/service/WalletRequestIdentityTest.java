package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletRequestIdentityTest {

  private static final IdempotencyKey KEY = IdempotencyKey.of("credit:semantic-identity");
  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000028");
  private static final Money AMOUNT = Money.krw(20L);

  @Test
  void matchesTheSuppliedSemanticFingerprintAndRejectsTheGenericOne() {
    OperationFingerprint semantic =
        OperationFingerprint.credit(
            WalletCaller.SETTLEMENT,
            WalletOperationKind.BET_REFUND,
            USER,
            AMOUNT,
            CreditCommand.Source.USER_LOCKED,
            CreditReason.REFUND);
    WalletRequestIdentity request =
        new WalletRequestIdentity(
            KEY, WalletCaller.SETTLEMENT, WalletOperationKind.BET_REFUND, USER, AMOUNT, semantic);
    WalletOperation matching = outcome(semantic.value());
    WalletOperation generic =
        outcome(
            OperationFingerprint.transfer(
                    WalletCaller.SETTLEMENT, WalletOperationKind.BET_REFUND, USER, AMOUNT)
                .value());

    assertThat(request.requireMatching(matching)).isSameAs(matching);
    assertThatThrownBy(() -> request.requireMatching(generic))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  private static WalletOperation outcome(String fingerprint) {
    return WalletOperation.succeeded(
        KEY,
        WalletCaller.SETTLEMENT,
        WalletOperationKind.BET_REFUND,
        USER,
        AMOUNT,
        fingerprint,
        UUID.fromString("019b76da-a000-7000-8000-000000000029"),
        Instant.parse("2026-08-21T00:00:00Z"));
  }
}
