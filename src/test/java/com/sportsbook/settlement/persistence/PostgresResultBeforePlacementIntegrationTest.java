package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletClient;
import com.sportsbook.settlement.client.WalletCreditPurpose;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.event.BaseResultEvents;
import com.sportsbook.settlement.event.BetPlacedListener;
import com.sportsbook.settlement.event.MatchResultListener;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.Acknowledgment;

class PostgresResultBeforePlacementIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private MatchResultListener results;
  @Autowired private BetPlacedListener placements;
  @Autowired private BetRepository bets;
  @MockBean private WalletClient wallet;

  @Test
  void catchesUpVoidedResultIntoOneSettledEventAndOutboxRecord() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    var result = BaseResultEvents.voided(eventId);
    var placement = BaseResultEvents.single(betId, userId, eventId, selectionId);
    Acknowledgment resultAck = mock(Acknowledgment.class);
    Acknowledgment placementAck = mock(Acknowledgment.class);
    when(wallet.credit(
            "settle:refund:" + betId, userId, Money.krw(100), WalletCreditPurpose.RETURNED_STAKE))
        .thenReturn(UUID.randomUUID());

    results.receive(BaseResultEvents.resultRecord(result), resultAck);
    placements.receive(BaseResultEvents.placementRecord(placement), placementAck);
    placements.receive(BaseResultEvents.placementRecord(placement), placementAck);

    var settled = bets.findWithSelectionsById(betId).orElseThrow();
    UUID acceptedCandidate =
        jdbc.queryForObject(
            "select accepted_candidate_id from match_result where event_id=?", UUID.class, eventId);
    assertThat(settled.status()).isEqualTo(SettlementStatus.SETTLED);
    assertThat(settled.result()).isEqualTo(SettlementResult.VOID);
    assertThat(settled.payout()).isEqualTo(Money.krw(100));
    assertThat(settled.selections().get(0).sourceCandidateId()).isEqualTo(acceptedCandidate);
    assertThat(jdbc.queryForObject("select count(*) from settlement_attempt", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetSettled'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetVoided'", Integer.class))
        .isZero();
    verify(wallet, times(1))
        .credit(
            "settle:refund:" + betId, userId, Money.krw(100), WalletCreditPurpose.RETURNED_STAKE);
    verify(resultAck).acknowledge();
    verify(placementAck, times(2)).acknowledge();
  }
}
