package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** SHA-256 of a versioned binary representation of semantic request identity. */
public record OperationFingerprint(String value) {
  private static final int HASH_LENGTH = 64;
  private static final int USER_TAG = 3;
  private static final int AMOUNT_TAG = 4;
  private static final int CURRENCY_TAG = 5;
  private static final int REVISION_TAG = 6;
  private static final int BET_TAG = 7;
  private static final int REVISION_NUMBER_TAG = 8;
  private static final int PREVIOUS_PAYOUT_TAG = 9;
  private static final int NEW_PAYOUT_TAG = 10;
  private static final int CREDIT_SOURCE_TAG = 11;
  private static final int CREDIT_REASON_TAG = 12;

  public OperationFingerprint {
    Objects.requireNonNull(value, "value");
    if (value.length() != HASH_LENGTH || !value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Operation fingerprint must be lower-case SHA-256 hex");
    }
  }

  public static OperationFingerprint transfer(
      WalletCaller caller, WalletOperationKind kind, UUID userId, Money amount) {
    return digest(base(caller, kind, userId, amount).toByteArray());
  }

  public static OperationFingerprint credit(
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      CreditCommand.Source source,
      CreditReason reason) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reason, "reason");
    WalletOperationKind expectedKind =
        reason == CreditReason.PAYOUT
            ? WalletOperationKind.BET_PAYOUT
            : WalletOperationKind.BET_REFUND;
    if (kind != expectedKind) {
      throw new IllegalArgumentException("Credit reason does not match operation kind");
    }
    CanonicalRequestEncoder encoded = base(caller, kind, userId, amount);
    encoded.text(CREDIT_SOURCE_TAG, source.name()).text(CREDIT_REASON_TAG, reason.name());
    return digest(encoded.toByteArray());
  }

  public static OperationFingerprint adjustment(
      WalletCaller caller,
      UUID userId,
      Money previousPayout,
      Money newPayout,
      UUID revisionId,
      UUID betId,
      long revisionNumber) {
    Objects.requireNonNull(previousPayout, "previousPayout");
    Objects.requireNonNull(newPayout, "newPayout");
    if (previousPayout.currency() != newPayout.currency()) {
      throw new IllegalArgumentException("Adjustment payout currencies must match");
    }
    long delta = Math.subtractExact(newPayout.amount(), previousPayout.amount());
    if (delta == Long.MIN_VALUE) {
      throw new ArithmeticException("Adjustment delta is not representable");
    }
    CanonicalRequestEncoder encoded =
        base(
            caller,
            WalletOperationKind.BET_ADJUSTMENT,
            userId,
            new Money(Math.abs(delta), previousPayout.currency()));
    encoded
        .uuid(REVISION_TAG, Objects.requireNonNull(revisionId, "revisionId"))
        .uuid(BET_TAG, Objects.requireNonNull(betId, "betId"))
        .number(REVISION_NUMBER_TAG, revisionNumber)
        .number(PREVIOUS_PAYOUT_TAG, previousPayout.amount())
        .number(NEW_PAYOUT_TAG, newPayout.amount());
    return digest(encoded.toByteArray());
  }

  private static CanonicalRequestEncoder base(
      WalletCaller caller, WalletOperationKind kind, UUID userId, Money amount) {
    Objects.requireNonNull(amount, "amount");
    return new CanonicalRequestEncoder()
        .text(1, Objects.requireNonNull(caller, "caller").name())
        .text(2, Objects.requireNonNull(kind, "kind").name())
        .uuid(USER_TAG, Objects.requireNonNull(userId, "userId"))
        .number(AMOUNT_TAG, amount.amount())
        .text(CURRENCY_TAG, amount.currency().name());
  }

  private static OperationFingerprint digest(byte[] canonical) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical);
      return new OperationFingerprint(HexFormat.of().formatHex(hash));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JVM lacks SHA-256", impossible);
    }
  }
}
