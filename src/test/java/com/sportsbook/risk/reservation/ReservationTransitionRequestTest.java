package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTransitionRequestTest {
  private static final BetId BET = BetId.of(new UUID(0, 1));
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);
  private static final RiskReservationProperties RESERVATIONS =
      new RiskReservationProperties(null, null);

  @Test
  void bindsCommitTokenAndRetention() {
    String token = "a".repeat(64);
    ReservationTransitionRequest request =
        ReservationTransitionRequest.commit(
            BET,
            token,
            NOW,
            RESERVATIONS,
            new RiskPatternProperties(null, null, null),
            new RiskHistoryProperties(null, 0));

    assertThat(request.keys())
        .containsExactly(ReservationKeys.lifecycle(BET), ReservationKeys.ACTIVE_COUNT);
    assertThat(request.arguments()).hasSize(12);
    assertThat(request.arguments().subList(0, 4))
        .containsExactly(
            "1",
            Long.toString(NOW.toEpochMilli()),
            Long.toString(RESERVATIONS.retention().toMillis()),
            token);
  }

  @Test
  void bindsReleaseWithoutReservationToken() {
    ReservationTransitionRequest request =
        ReservationTransitionRequest.release(BET, NOW, RESERVATIONS);

    assertThat(request.arguments())
        .containsExactly(
            "1",
            Long.toString(NOW.toEpochMilli()),
            Long.toString(RESERVATIONS.retention().toMillis()));
  }

  @Test
  void rejectsNoncanonicalCommitTokens() {
    assertThatThrownBy(
            () ->
                ReservationTransitionRequest.commit(
                    BET,
                    "A".repeat(64),
                    NOW,
                    RESERVATIONS,
                    new RiskPatternProperties(null, null, null),
                    new RiskHistoryProperties(null, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
