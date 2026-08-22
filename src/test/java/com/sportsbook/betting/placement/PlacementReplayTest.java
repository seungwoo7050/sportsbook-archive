package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sportsbook.betting.error.DuplicateBetException;
import com.sportsbook.betting.error.PersistedRejectionException;
import com.sportsbook.protocol.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementReplayTest {

  @Test
  void rethrowsTheOriginalDefinitiveVerdict() {
    UUID actorId = UUID.randomUUID();
    PlacementRequest request =
        PlacementRequest.rejected(
            "request-1",
            actorId,
            "a".repeat(64),
            ErrorCode.VALIDATION_FAILED,
            "invalid slip",
            Instant.EPOCH);

    PersistedRejectionException failure =
        catchThrowableOfType(
            () -> PlacementReplay.request(request, actorId, "a".repeat(64), ignored -> null),
            PersistedRejectionException.class);

    assertThat(failure.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(failure).hasMessage("invalid slip");
  }

  @Test
  void rejectsCrossActorKeyReplayBeforeLoadingTheBet() {
    PlacementRequest request =
        PlacementRequest.rejected(
            "request-1",
            UUID.randomUUID(),
            "a".repeat(64),
            ErrorCode.VALIDATION_FAILED,
            "invalid slip",
            Instant.EPOCH);

    assertThat(
            catchThrowableOfType(
                    () ->
                        PlacementReplay.request(
                            request, UUID.randomUUID(), "a".repeat(64), ignored -> null),
                    DuplicateBetException.class)
                .errorCode())
        .isEqualTo(ErrorCode.DUPLICATE_BET);
  }
}
