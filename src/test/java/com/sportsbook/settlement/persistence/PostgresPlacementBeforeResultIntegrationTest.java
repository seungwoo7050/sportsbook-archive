package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class PostgresPlacementBeforeResultIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private MatchResultListener results;
  @Autowired private BetPlacedListener placements;
  @Autowired private BetRepository bets;
  @MockBean private WalletClient wallet;

  @Test
  void settlesPlacementWhenItsFirstResultAndExactReplayArrive() {
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    var placement = BaseResultEvents.single(betId, userId, eventId, UUID.randomUUID());
    var result = BaseResultEvents.voided(eventId);
    Acknowledgment placementAck = mock(Acknowledgment.class);
    Acknowledgment resultAck = mock(Acknowledgment.class);
    when(wallet.credit(
            "settle:refund:" + betId, userId, Money.krw(100), WalletCreditPurpose.RETURNED_STAKE))
        .thenReturn(UUID.randomUUID());

    placements.receive(BaseResultEvents.placementRecord(placement), placementAck);
    assertThat(bets.findById(betId).orElseThrow().status()).isEqualTo(SettlementStatus.PENDING);
    results.receive(BaseResultEvents.resultRecord(result), resultAck);
    results.receive(BaseResultEvents.resultRecord(result), resultAck);

    var settled = bets.findWithSelectionsById(betId).orElseThrow();
    assertThat(settled.status()).isEqualTo(SettlementStatus.SETTLED);
    assertThat(settled.selections().get(0).sourceCandidateId()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetSettled'", Integer.class))
        .isEqualTo(1);
    verify(wallet, times(1))
        .credit(
            "settle:refund:" + betId, userId, Money.krw(100), WalletCreditPurpose.RETURNED_STAKE);
    verify(placementAck).acknowledge();
    verify(resultAck, times(2)).acknowledge();
  }
}
