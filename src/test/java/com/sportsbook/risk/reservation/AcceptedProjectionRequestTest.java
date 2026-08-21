package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptedProjectionRequestTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final String FINGERPRINT = "a".repeat(64);

  @Test
  void encodesIdentityExposureAndPolicyArguments() {
    AcceptedProjectionRequest request =
        AcceptedProjectionRequest.from(
            command(),
            FINGERPRINT,
            new RiskReservationProperties(null, null),
            new RiskPatternProperties(null, null, null),
            new RiskHistoryProperties(null, 2));

    assertThat(request.keys())
        .containsExactly(ReservationKeys.lifecycle(BET), ReservationKeys.acceptedFingerprint(BET));
    assertThat(request.arguments()).hasSize(18);
    assertThat(request.arguments().subList(3, 10))
        .containsExactly(
            FINGERPRINT,
            USER.value().toString(),
            BET.value().toString(),
            "25",
            "KRW",
            "2",
            "00000000-0000-0000-0000-000000000003,00000000-0000-0000-0000-000000000004");
  }

  @Test
  void rejectsNoncanonicalFingerprints() {
    assertThatThrownBy(
            () ->
                AcceptedProjectionRequest.from(
                    command(),
                    "A".repeat(64),
                    new RiskReservationProperties(null, null),
                    new RiskPatternProperties(null, null, null),
                    new RiskHistoryProperties(null, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lowercase SHA-256");
  }

  private static RiskCheckCommand command() {
    return new RiskCheckCommand(
        USER,
        BET,
        new Money(25, Currency.KRW),
        List.of(SelectionId.of(new UUID(0, 3)), SelectionId.of(new UUID(0, 4))),
        Instant.ofEpochMilli(2_000_000));
  }
}
