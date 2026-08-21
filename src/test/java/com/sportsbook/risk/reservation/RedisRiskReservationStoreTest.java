package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.service.RiskCheckCommand;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisRiskReservationStoreTest extends RedisTestSupport {
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);

  @Test
  void executesAdmissionCommitAndReleaseThroughTheTypedPort() {
    RiskReservationStore store = store();
    RiskCheckCommand committed = command(1);
    ReservationDecision reserved = store.reserve(committed);

    assertThat(reserved.approved()).isTrue();
    assertThat(store.commit(committed.betId(), reserved.token(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);

    RiskCheckCommand released = command(2);
    assertThat(store.reserve(released).approved()).isTrue();
    assertThat(store.release(released.betId(), NOW.plusMillis(1)))
        .isEqualTo(ReservationTransition.APPLIED);
  }

  @Test
  void executesAcceptedProjectionThroughTheTypedPort() {
    RiskReservationStore store = store();
    RiskCheckCommand accepted = command(3);
    String fingerprint = ReservationFingerprint.of(accepted);

    assertThat(store.projectAccepted(accepted, fingerprint))
        .isEqualTo(ReservationTransition.APPLIED);
    assertThat(store.projectAccepted(accepted, fingerprint))
        .isEqualTo(ReservationTransition.REPLAYED);
  }

  private RiskReservationStore store() {
    return new RedisRiskReservationStore(
        redis,
        new RiskLimitProperties(null, null, null, null, 100),
        new RiskPatternProperties(null, null, null),
        new RiskReservationProperties(null, null),
        new RiskHistoryProperties(null, 0),
        new ReservationWireMapper(new ObjectMapper()));
  }

  private static RiskCheckCommand command(long value) {
    return new RiskCheckCommand(
        UserId.of(new UUID(0, 1)),
        BetId.of(new UUID(0, value)),
        new Money(10, Currency.KRW),
        List.of(SelectionId.of(new UUID(0, value + 10))),
        NOW);
  }
}
