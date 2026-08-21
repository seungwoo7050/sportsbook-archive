package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationFingerprintTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId FIRST = SelectionId.of(new UUID(0, 3));
  private static final SelectionId SECOND = SelectionId.of(new UUID(0, 4));

  @Test
  void ignoresSelectionOrderAndEvaluationTime() {
    String token = fingerprint(USER, BET, Money.krw(100), List.of(FIRST, SECOND), Instant.EPOCH);

    assertThat(token).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(
            fingerprint(
                USER, BET, Money.krw(100), List.of(SECOND, FIRST), Instant.EPOCH.plusSeconds(10)))
        .isEqualTo(token);
  }

  @Test
  void changesForEveryRequestIdentityDimension() {
    String token = fingerprint(USER, BET, Money.krw(100), List.of(FIRST), Instant.EPOCH);

    assertThat(
            fingerprint(
                UserId.of(new UUID(0, 9)), BET, Money.krw(100), List.of(FIRST), Instant.EPOCH))
        .isNotEqualTo(token);
    assertThat(
            fingerprint(
                USER, BetId.of(new UUID(0, 9)), Money.krw(100), List.of(FIRST), Instant.EPOCH))
        .isNotEqualTo(token);
    assertThat(fingerprint(USER, BET, Money.krw(101), List.of(FIRST), Instant.EPOCH))
        .isNotEqualTo(token);
    assertThat(fingerprint(USER, BET, Money.usd(100), List.of(FIRST), Instant.EPOCH))
        .isNotEqualTo(token);
    assertThat(fingerprint(USER, BET, Money.krw(100), List.of(SECOND), Instant.EPOCH))
        .isNotEqualTo(token);
  }

  private static String fingerprint(
      UserId userId, BetId betId, Money stake, List<SelectionId> selections, Instant evaluatedAt) {
    return ReservationFingerprint.of(
        new RiskCheckCommand(userId, betId, stake, selections, evaluatedAt));
  }
}
