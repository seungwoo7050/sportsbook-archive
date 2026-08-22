package com.sportsbook.settlement.persistence;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.RevisionRecoveryScanner;
import com.sportsbook.settlement.execution.SettlementAttemptRecovery;
import com.sportsbook.settlement.lifecycle.LifecycleTombstoneScanner;
import com.sportsbook.settlement.outbox.OutboxPublisher;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "spring.kafka.listener.auto-startup=false",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "settlement.wallet.api-key=0123456789abcdef0123456789abcdef",
      "settlement.outbox.interval=PT24H",
      "settlement.runtime.recovery-interval=PT24H"
    })
abstract class PostgresIntegrationSupport {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected JdbcTemplate jdbc;
  @MockBean private OutboxPublisher outboxPublisher;
  @MockBean private LifecycleTombstoneScanner lifecycleScanner;
  @MockBean private SettlementAttemptRecovery attemptRecovery;
  @MockBean private RevisionRecoveryScanner revisionRecovery;

  @BeforeEach
  void resetBusinessTables() {
    jdbc.execute(
        "truncate table bet, outbox_event, match_result, result_candidate, "
            + "event_lifecycle_observation, event_lifecycle_tombstone "
            + "restart identity cascade");
  }

  protected PendingBet insertPendingBet(UUID eventId) {
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.parse("2026-08-22T00:00:00Z"));
    jdbc.update(
        """
        insert into bet (bet_id, user_id, slip_type, stake_amount, stake_currency,
            status, requested_at, created_at, updated_at)
        values (?, ?, 'SINGLE', 100, 'KRW', 'PENDING', ?, ?, ?)
        """,
        betId,
        userId,
        now,
        now,
        now);
    jdbc.update(
        """
        insert into bet_selection (selection_row_id, bet_id, leg_index, event_id,
            market_id, selection_id, odds)
        values (?, ?, 0, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        betId,
        eventId,
        UUID.randomUUID(),
        selectionId,
        new BigDecimal("2.0000"));
    return new PendingBet(betId, userId, eventId, selectionId);
  }

  protected PendingMultiple insertPendingMultiple(Map<UUID, UUID> eventSelections) {
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.parse("2026-08-22T00:00:00Z"));
    jdbc.update(
        """
        insert into bet (bet_id, user_id, slip_type, stake_amount, stake_currency,
            status, requested_at, created_at, updated_at)
        values (?, ?, 'MULTIPLE', 100, 'KRW', 'PENDING', ?, ?, ?)
        """,
        betId,
        userId,
        now,
        now,
        now);
    int index = 0;
    for (var selection : eventSelections.entrySet()) {
      jdbc.update(
          """
          insert into bet_selection (selection_row_id, bet_id, leg_index, event_id,
              market_id, selection_id, odds) values (?, ?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          betId,
          index++,
          selection.getKey(),
          UUID.randomUUID(),
          selection.getValue(),
          new BigDecimal("2.0000"));
    }
    return new PendingMultiple(betId, userId, Map.copyOf(eventSelections));
  }

  protected UUID insertResultCandidate(
      UUID eventId, UUID selectionId, SettlementResult outcome, Instant settledAt, String state) {
    UUID candidateId = UUID.randomUUID();
    Timestamp timestamp = Timestamp.from(settledAt);
    jdbc.update(
        "insert into result_candidate (candidate_id,event_id,fingerprint,mode,settled_at,"
            + "received_at,state,decided_at) values (?,?,?,'COMPLETED',?,?,?,?)",
        candidateId,
        eventId,
        candidateId.toString().replace("-", "").repeat(2),
        timestamp,
        timestamp,
        state,
        "PENDING".equals(state) ? null : timestamp);
    jdbc.update(
        "insert into result_candidate_selection (candidate_id,selection_id,outcome) "
            + "values (?,?,?)",
        candidateId,
        selectionId,
        outcome.name());
    return candidateId;
  }

  protected void acceptResult(
      PendingBet bet, UUID candidateId, SettlementResult outcome, Instant settledAt) {
    Timestamp timestamp = Timestamp.from(settledAt);
    jdbc.update(
        "insert into match_result (event_id,mode,settled_at,received_at,"
            + "accepted_candidate_id) values (?,'COMPLETED',?,?,?)",
        bet.eventId(),
        timestamp,
        timestamp,
        candidateId);
    jdbc.update(
        "insert into match_selection_result (event_id,selection_id,outcome) values (?,?,?)",
        bet.eventId(),
        bet.selectionId(),
        outcome.name());
  }

  protected void settleBet(
      PendingBet bet, UUID sourceCandidateId, SettlementResult result, long payout) {
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-08-22T00:00:00Z"));
    jdbc.update(
        "update bet set status='SETTLED',result=?,payout_amount=?,payout_currency='KRW',"
            + "settled_at=?,updated_at=? where bet_id=?",
        result.name(),
        payout,
        timestamp,
        timestamp,
        bet.betId());
    jdbc.update(
        "update bet_selection set outcome=?,source_candidate_id=? where bet_id=?",
        result.name(),
        sourceCandidateId,
        bet.betId());
  }

  protected record PendingBet(UUID betId, UUID userId, UUID eventId, UUID selectionId) {}

  protected record PendingMultiple(UUID betId, UUID userId, Map<UUID, UUID> eventSelections) {}
}
