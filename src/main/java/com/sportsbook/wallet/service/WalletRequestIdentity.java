package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import java.util.Objects;
import java.util.UUID;

/** Canonical transfer identity used for both first-writer validation and immutable replay. */
record WalletRequestIdentity(
    IdempotencyKey key,
    WalletCaller caller,
    WalletOperationKind kind,
    UUID userId,
    Money amount,
    OperationFingerprint operationFingerprint) {

  WalletRequestIdentity(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount) {
    this(
        key,
        caller,
        kind,
        userId,
        amount,
        OperationFingerprint.transfer(caller, kind, userId, amount));
  }

  WalletRequestIdentity {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(operationFingerprint, "operationFingerprint");
  }

  String fingerprint() {
    return operationFingerprint.value();
  }

  WalletOperation requireMatching(WalletOperation operation) {
    if (!operation.idempotencyKey().equals(key.value())
        || operation.caller() != caller
        || operation.kind() != kind
        || !operation.userId().equals(userId)
        || !operation.requestAmount().equals(amount)
        || !operation.requestFingerprint().equals(fingerprint())) {
      throw new IdempotencyConflictException(key);
    }
    return operation;
  }
}
