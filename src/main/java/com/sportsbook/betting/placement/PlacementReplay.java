package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.DuplicateBetException;
import com.sportsbook.betting.error.PersistedRejectionException;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.error.ErrorCode;
import java.util.UUID;
import java.util.function.Function;

final class PlacementReplay {

  private PlacementReplay() {}

  static Bet bet(Bet existing, UUID actorId, String fingerprint) {
    validateIdentity(existing.userId(), existing.requestFingerprint(), actorId, fingerprint);
    if (existing.status() == BetStatus.REJECTED) {
      throw persisted(existing.rejectionReason(), existing.rejectionDetail());
    }
    return existing;
  }

  static Bet request(
      PlacementRequest request, UUID actorId, String fingerprint, Function<UUID, Bet> betLoader) {
    validateIdentity(request.userId(), request.requestFingerprint(), actorId, fingerprint);
    if (!request.outcome().hasBet()) {
      throw persisted(request.errorCode(), request.errorDetail());
    }
    return bet(betLoader.apply(request.betId()), actorId, fingerprint);
  }

  private static void validateIdentity(
      UUID savedActor, String savedFingerprint, UUID actorId, String fingerprint) {
    if (!savedActor.equals(actorId)) {
      throw new DuplicateBetException("Idempotency-Key cannot be reused by this actor");
    }
    if (savedFingerprint != null
        && !savedFingerprint.equals(fingerprint)
        && !savedFingerprint.startsWith("legacy-")) {
      throw new DuplicateBetException(
          "Idempotency-Key cannot be reused with a different request payload");
    }
  }

  private static PersistedRejectionException persisted(String rawCode, String detail) {
    ErrorCode code;
    try {
      code = ErrorCode.valueOf(rawCode);
    } catch (RuntimeException invalidCode) {
      code = ErrorCode.INTERNAL_ERROR;
    }
    String replayDetail = detail == null ? rawCode : detail;
    return new PersistedRejectionException(code, replayDetail);
  }
}
