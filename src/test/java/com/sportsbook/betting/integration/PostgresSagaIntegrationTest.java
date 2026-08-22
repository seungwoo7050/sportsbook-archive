package com.sportsbook.betting.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.outbox.OutboxEvent;
import com.sportsbook.betting.outbox.OutboxEventRepository;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.PlacementRequestRepository;
import com.sportsbook.betting.placement.BetStore;
import com.sportsbook.betting.support.PostgresIntegrationSupport;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresSagaIntegrationTest extends PostgresIntegrationSupport {

  @Autowired BetStore store;
  @Autowired BetRepository bets;
  @Autowired OutboxEventRepository outbox;
  @Autowired PlacementRequestRepository requests;
  @Autowired JdbcTemplate jdbc;

  @Test
  void migratesAndCommitsPlacementWithItsOutbox() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Bet bet =
        Bet.pending(
            new BetDraft(
                betId,
                userId,
                "B-2026-08-22-00000001",
                new BetSlipType.Single(),
                Money.krw(1_000),
                Money.krw(2_000),
                IdempotencyKey.of("postgres-saga-" + betId),
                "a".repeat(64),
                now),
            List.of(
                BetLeg.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Odds.ofDecimal("2.0"))));

    store.savePending(bet);
    store.recordRiskReservation(
        betId, now.plusSeconds(120), "b".repeat(64), false, now.plusSeconds(1));
    store.confirmWallet(betId, UUID.randomUUID(), now.plusSeconds(2));
    store.commitRisk(betId, now.plusSeconds(3));
    UUID outboxId = UUID.randomUUID();
    store.acceptAndEnqueue(
        betId,
        OutboxEvent.pending(
            outboxId,
            "bet.placed.v1",
            userId.toString(),
            "BetPlacedRequested",
            new byte[] {1},
            now),
        now.plusSeconds(4));

    Bet reloaded = bets.findWithLegsByBetId(betId).orElseThrow();
    assertThat(reloaded.status()).isEqualTo(BetStatus.ACCEPTED);
    assertThat(reloaded.riskReservationToken()).isEqualTo("b".repeat(64));
    assertThat(reloaded.legs()).hasSize(1);
    assertThat(outbox.findById(outboxId)).isPresent();
    assertThat(requests.findById("postgres-saga-" + betId)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
        .isEqualTo(10);
  }
}
