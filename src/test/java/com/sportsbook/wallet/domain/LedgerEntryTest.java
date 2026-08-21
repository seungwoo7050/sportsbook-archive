package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000003");
  private static final IdempotencyKey KEY = IdempotencyKey.of("deposit:test");

  @Test
  void constructsACompleteMatchedPair() {
    UUID groupId = UUID.randomUUID();
    LedgerEntry.TransferLeg destination =
        new LedgerEntry.TransferLeg(USER_ID, BalanceBucket.AVAILABLE);
    LedgerEntry.TransferLeg source =
        new LedgerEntry.TransferLeg(SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE);

    LedgerEntry.Pair pair =
        LedgerEntry.pair(
            destination, source, Money.krw(500L), LedgerReason.DEPOSIT, KEY, groupId, NOW);

    assertThat(pair.debit().accountId()).isEqualTo(USER_ID);
    assertThat(pair.debit().side()).isEqualTo(LedgerSide.DEBIT);
    assertThat(pair.credit().accountId()).isEqualTo(SystemAccountIds.EXTERNAL_PAYMENT);
    assertThat(pair.credit().side()).isEqualTo(LedgerSide.CREDIT);
    assertThat(pair.debit().money()).isEqualTo(Money.krw(500L));
    assertThat(pair.credit().money()).isEqualTo(pair.debit().money());
    assertThat(pair.debit().reason()).isEqualTo(LedgerReason.DEPOSIT);
    assertThat(pair.debit().idempotencyKey()).isEqualTo(KEY.value());
    assertThat(pair.debit().operationGroupId()).isEqualTo(groupId);
    assertThat(pair.credit().operationGroupId()).isEqualTo(groupId);
    assertThat(pair.debit().createdAt()).isEqualTo(NOW);
    assertThat(pair.debit().entryId()).isNotEqualTo(pair.credit().entryId());
  }

  @Test
  void rejectsTheSameTransferLegOnBothSides() {
    LedgerEntry.TransferLeg leg = new LedgerEntry.TransferLeg(USER_ID, BalanceBucket.AVAILABLE);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                LedgerEntry.pair(
                    leg, leg, Money.krw(1L), LedgerReason.DEPOSIT, KEY, UUID.randomUUID(), NOW));
  }

  @Test
  void rejectsNonPositiveLedgerMoney() {
    LedgerEntry.TransferLeg destination =
        new LedgerEntry.TransferLeg(USER_ID, BalanceBucket.AVAILABLE);
    LedgerEntry.TransferLeg source =
        new LedgerEntry.TransferLeg(SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                LedgerEntry.pair(
                    destination,
                    source,
                    Money.zero(Currency.KRW),
                    LedgerReason.DEPOSIT,
                    KEY,
                    UUID.randomUUID(),
                    NOW));
  }
}
