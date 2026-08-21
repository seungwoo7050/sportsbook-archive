package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationFingerprintTest {
  private static final UUID USER = UUID.fromString("00000000-0000-7000-8000-000000000123");

  @Test
  void locksTheTransferVector() {
    OperationFingerprint result =
        OperationFingerprint.transfer(
            WalletCaller.PLATFORM, WalletOperationKind.DEPOSIT, USER, Money.krw(1_000L));

    assertThat(result.value())
        .isEqualTo("d544e877620dfa1a88ed3affc6b00ae586064c9ec6633f9cf27c17b49fda49c5");
  }

  @Test
  void locksTheAdjustmentVector() {
    OperationFingerprint result =
        OperationFingerprint.adjustment(
            WalletCaller.SETTLEMENT,
            USER,
            Money.krw(1_000L),
            Money.krw(700L),
            UUID.fromString("00000000-0000-7000-8000-000000000456"),
            UUID.fromString("00000000-0000-7000-8000-000000000789"),
            2L);

    assertThat(result.value())
        .isEqualTo("5c95e5e161c733bca2c4f80de2118c1d6fbf037ad14ecb25948e55cbabd4e40d");
  }

  @Test
  void rejectsNonCanonicalDigestText() {
    assertThatIllegalArgumentException().isThrownBy(() -> new OperationFingerprint("a".repeat(63)));
    assertThatIllegalArgumentException().isThrownBy(() -> new OperationFingerprint("A".repeat(64)));
  }

  @Test
  void bindsCreditSourceAndReason() {
    OperationFingerprint baseline =
        OperationFingerprint.credit(
            WalletCaller.SETTLEMENT,
            WalletOperationKind.BET_REFUND,
            USER,
            Money.krw(1_000L),
            CreditCommand.Source.USER_LOCKED,
            CreditReason.REFUND);
    OperationFingerprint changedSource =
        OperationFingerprint.credit(
            WalletCaller.SETTLEMENT,
            WalletOperationKind.BET_REFUND,
            USER,
            Money.krw(1_000L),
            CreditCommand.Source.HOUSE_POOL,
            CreditReason.REFUND);
    OperationFingerprint changedReason =
        OperationFingerprint.credit(
            WalletCaller.SETTLEMENT,
            WalletOperationKind.BET_REFUND,
            USER,
            Money.krw(1_000L),
            CreditCommand.Source.USER_LOCKED,
            CreditReason.VOID);

    assertThat(baseline).isNotEqualTo(changedSource).isNotEqualTo(changedReason);
  }
}
