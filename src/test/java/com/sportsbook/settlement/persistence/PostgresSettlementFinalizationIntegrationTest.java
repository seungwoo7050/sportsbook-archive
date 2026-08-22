package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementFinalizer;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresSettlementFinalizationIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private SettlementAttemptRepository attempts;
  @Autowired private SettlementFinalizer finalizer;

  @Test
  void commitsBetAndOutboxWithOneDatabaseTimestamp() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    SettlementAttempt attempt = claimResolved(bet);

    assertThat(finalizer.settle(attempt)).isTrue();

    Map<String, Object> clocks =
        jdbc.queryForMap(
            "select b.settled_at, o.created_at from bet b join outbox_event o "
                + "on o.partition_key=? where b.bet_id=?",
            bet.eventId().toString(),
            bet.betId());
    assertThat(clocks.get("settled_at")).isEqualTo(clocks.get("created_at"));
    byte[] payload =
        jdbc.queryForObject(
            "select payload from outbox_event where partition_key=?",
            byte[].class,
            bet.eventId().toString());
    BetSettled event = new StrictAvroDecoder().decode(payload, BetSettled.class);
    assertThat(event.getSettledAt()).isEqualTo(((Timestamp) clocks.get("settled_at")).toInstant());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_attempt where bet_id=?",
                Integer.class,
                bet.betId()))
        .isZero();
  }

  @Test
  void refusesAnExpiredLeaseWithoutChangingProjectionOrOutbox() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    SettlementAttempt attempt = claimResolved(bet);
    jdbc.update(
        "update settlement_attempt set lease_until=current_timestamp-interval '1 second' "
            + "where bet_id=?",
        bet.betId());

    assertThat(finalizer.settle(attempt)).isFalse();

    assertThat(
            jdbc.queryForObject(
                "select status='PENDING' from bet where bet_id=?", Boolean.class, bet.betId()))
        .isTrue();
    assertThat(jdbc.queryForObject("select count(*) from outbox_event", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from settlement_attempt where bet_id=?",
                Integer.class,
                bet.betId()))
        .isOne();
  }

  private SettlementAttempt claimResolved(PendingBet bet) {
    jdbc.update("update bet_selection set outcome='WON' where bet_id=?", bet.betId());
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
    SettlementAttemptDraft draft =
        SettlementAttemptDraft.resolved(bet.betId(), bet.eventId(), SettlementResult.WON, money);
    return attempts.claimPending(draft, Duration.ofSeconds(30)).orElseThrow();
  }
}
