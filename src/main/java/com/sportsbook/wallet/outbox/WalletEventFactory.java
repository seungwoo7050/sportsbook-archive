package com.sportsbook.wallet.outbox;

import com.sportsbook.protocol.event.WalletCredited;
import com.sportsbook.protocol.event.WalletDebitFailed;
import com.sportsbook.protocol.event.WalletDebitFailureReason;
import com.sportsbook.protocol.event.WalletDebited;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.DebitCommand;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WalletEventFactory {

  public static final String DEBITED_TOPIC = "wallet.debited.v1";
  public static final String DEBIT_FAILED_TOPIC = "wallet.debit-failed.v1";
  public static final String CREDITED_TOPIC = "wallet.credited.v1";

  public PendingOutboxMessage credited(
      CreditCommand command, UUID destinationLedgerEntryId, Instant occurredAt) {
    WalletCredited event =
        new WalletCredited(
            command.userId().toString(),
            eventMoney(command.amount()),
            command.idempotencyKey().value(),
            destinationLedgerEntryId.toString(),
            com.sportsbook.protocol.event.WalletCreditReason.valueOf(command.reason().name()),
            wireTime(occurredAt));
    return PendingOutboxMessage.create(
        command.idempotencyKey().value(),
        CREDITED_TOPIC,
        command.userId().toString(),
        WalletCredited.getClassSchema().getName(),
        command.idempotencyKey().value(),
        AvroSerializer.serialize(event),
        occurredAt);
  }

  public PendingOutboxMessage debited(
      DebitCommand command, UUID destinationLedgerEntryId, Instant occurredAt) {
    WalletDebited event =
        new WalletDebited(
            command.userId().toString(),
            eventMoney(command.amount()),
            command.idempotencyKey().value(),
            destinationLedgerEntryId.toString(),
            wireTime(occurredAt));
    return pending(
        command,
        DEBITED_TOPIC,
        WalletDebited.getClassSchema().getName(),
        AvroSerializer.serialize(event),
        occurredAt);
  }

  public PendingOutboxMessage debitFailed(
      DebitCommand command, WalletFailureSnapshot failure, Instant occurredAt) {
    WalletDebitFailed event =
        new WalletDebitFailed(
            command.userId().toString(),
            eventMoney(command.amount()),
            command.idempotencyKey().value(),
            failureReason(failure.code()),
            wireTime(occurredAt));
    return pending(
        command,
        DEBIT_FAILED_TOPIC,
        WalletDebitFailed.getClassSchema().getName(),
        AvroSerializer.serialize(event),
        occurredAt);
  }

  private PendingOutboxMessage pending(
      DebitCommand command, String topic, String schema, byte[] payload, Instant occurredAt) {
    return PendingOutboxMessage.create(
        command.idempotencyKey().value(),
        topic,
        command.userId().toString(),
        schema,
        command.idempotencyKey().value(),
        payload,
        occurredAt);
  }

  private com.sportsbook.protocol.event.Money eventMoney(Money money) {
    return new com.sportsbook.protocol.event.Money(money.amount(), money.currency().name());
  }

  private WalletDebitFailureReason failureReason(WalletFailureCode code) {
    return switch (code) {
      case INSUFFICIENT_BALANCE -> WalletDebitFailureReason.INSUFFICIENT_BALANCE;
      case ACCOUNT_SUSPENDED -> WalletDebitFailureReason.ACCOUNT_SUSPENDED;
      case ACCOUNT_NOT_FOUND -> WalletDebitFailureReason.ACCOUNT_NOT_FOUND;
      case CURRENCY_MISMATCH -> WalletDebitFailureReason.CURRENCY_MISMATCH;
      case AMOUNT_OUT_OF_RANGE ->
          throw new IllegalArgumentException("Debit failure has no shared reason: " + code);
    };
  }

  private Instant wireTime(Instant occurredAt) {
    return occurredAt.truncatedTo(ChronoUnit.MILLIS);
  }
}
