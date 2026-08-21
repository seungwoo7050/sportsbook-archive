package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.WalletCredited;
import com.sportsbook.protocol.event.WalletDebitFailed;
import com.sportsbook.protocol.event.WalletDebitFailureReason;
import com.sportsbook.protocol.event.WalletDebited;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import com.sportsbook.wallet.service.command.DebitCommand;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WalletEventFactoryTest {

  private static final UUID USER_ID = UUID.fromString("0198ca71-8000-7000-8000-000000000001");
  private static final UUID BET_ID = UUID.fromString("0198ca71-8000-7000-8000-000000000002");
  private static final UUID LEDGER_ENTRY_ID =
      UUID.fromString("0198ca71-8000-7000-8000-000000000003");
  private static final Instant NOW = Instant.parse("2026-08-21T00:00:00.123456Z");

  private final WalletEventFactory factory = new WalletEventFactory();
  private final DebitCommand command =
      new DebitCommand(USER_ID, Money.krw(1_000L), IdempotencyKey.of(BET_ID.toString()));

  @Test
  void buildsADebitedMessageWithTheDestinationLedgerRow() {
    PendingOutboxMessage message = factory.debited(command, LEDGER_ENTRY_ID, NOW);

    WalletDebited event = AvroSerializer.deserialize(message.payload(), WalletDebited.class);
    assertCommon(message, WalletEventFactory.DEBITED_TOPIC, "WalletDebited");
    assertThat(event.getUserId()).isEqualTo(USER_ID.toString());
    assertThat(event.getAmount().getAmount()).isEqualTo(1_000L);
    assertThat(event.getAmount().getCurrency()).isEqualTo("KRW");
    assertThat(event.getLedgerTxId()).isEqualTo(LEDGER_ENTRY_ID.toString());
    assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-08-21T00:00:00.123Z"));
  }

  @ParameterizedTest
  @MethodSource("failureReasons")
  void mapsEveryDurableDebitFailure(
      WalletFailureCode code, WalletDebitFailureReason expectedReason) {
    PendingOutboxMessage message =
        factory.debitFailed(command, WalletFailureSnapshot.of(code, "failed"), NOW);

    WalletDebitFailed event =
        AvroSerializer.deserialize(message.payload(), WalletDebitFailed.class);
    assertCommon(message, WalletEventFactory.DEBIT_FAILED_TOPIC, "WalletDebitFailed");
    assertThat(event.getReason()).isEqualTo(expectedReason);
    assertThat(event.getRequestedAmount().getAmount()).isEqualTo(1_000L);
  }

  @ParameterizedTest
  @MethodSource("creditReasons")
  void mapsEveryCreditedReason(CreditReason reason) {
    CreditCommand credit =
        new CreditCommand(
            USER_ID,
            Money.krw(1_000L),
            CreditCommand.Source.HOUSE_POOL,
            reason,
            IdempotencyKey.of("credit:" + reason.name().toLowerCase()));

    PendingOutboxMessage message = factory.credited(credit, LEDGER_ENTRY_ID, NOW);

    WalletCredited event = AvroSerializer.deserialize(message.payload(), WalletCredited.class);
    assertThat(message.topic()).isEqualTo(WalletEventFactory.CREDITED_TOPIC);
    assertThat(message.partitionKey()).isEqualTo(USER_ID.toString());
    assertThat(event.getReason().name()).isEqualTo(reason.name());
    assertThat(event.getLedgerTxId()).isEqualTo(LEDGER_ENTRY_ID.toString());
  }

  private void assertCommon(PendingOutboxMessage message, String topic, String schema) {
    assertThat(message.operationKey()).isEqualTo(BET_ID.toString());
    assertThat(message.deduplicationKey()).isEqualTo(BET_ID.toString());
    assertThat(message.partitionKey()).isEqualTo(USER_ID.toString());
    assertThat(message.topic()).isEqualTo(topic);
    assertThat(message.schemaName()).isEqualTo(schema);
  }

  private static Stream<Arguments> failureReasons() {
    return Stream.of(
        Arguments.of(
            WalletFailureCode.INSUFFICIENT_BALANCE, WalletDebitFailureReason.INSUFFICIENT_BALANCE),
        Arguments.of(
            WalletFailureCode.ACCOUNT_SUSPENDED, WalletDebitFailureReason.ACCOUNT_SUSPENDED),
        Arguments.of(
            WalletFailureCode.ACCOUNT_NOT_FOUND, WalletDebitFailureReason.ACCOUNT_NOT_FOUND),
        Arguments.of(
            WalletFailureCode.CURRENCY_MISMATCH, WalletDebitFailureReason.CURRENCY_MISMATCH));
  }

  private static Stream<CreditReason> creditReasons() {
    return Stream.of(CreditReason.values());
  }
}
