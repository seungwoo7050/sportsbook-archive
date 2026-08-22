package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.Acknowledgment;

class PostgresPartialMultipleResultIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private MatchResultListener results;
  @Autowired private BetPlacedListener placements;
  @Autowired private BetRepository bets;
  @MockBean private WalletClient wallet;

  @Test
  void waitsForEveryEventBeforeClaimingAndSettlingAMultiple() {
    UUID firstEvent = UUID.randomUUID();
    UUID secondEvent = UUID.randomUUID();
    UUID firstSelection = UUID.randomUUID();
    UUID secondSelection = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    var selections = new LinkedHashMap<UUID, UUID>();
    selections.put(firstEvent, firstSelection);
    selections.put(secondEvent, secondSelection);
    var placement = BaseResultEvents.multiple(betId, userId, selections);
    Acknowledgment acknowledgment = mock(Acknowledgment.class);
    when(wallet.credit(
            "settle:refund:" + betId, userId, Money.krw(100), WalletCreditPurpose.RETURNED_STAKE))
        .thenReturn(UUID.randomUUID());
    when(wallet.credit(
            "settle:payout:" + betId, userId, Money.krw(300), WalletCreditPurpose.PROFIT_PAYOUT))
        .thenReturn(UUID.randomUUID());

    placements.receive(BaseResultEvents.placementRecord(placement), acknowledgment);
    results.receive(
        BaseResultEvents.resultRecord(
            BaseResultEvents.completed(firstEvent, firstSelection, SettlementResult.WON)),
        acknowledgment);

    assertThat(bets.findWithSelectionsById(betId).orElseThrow().status())
        .isEqualTo(SettlementStatus.PENDING);
    assertThat(jdbc.queryForObject("select count(*) from settlement_attempt", Integer.class))
        .isZero();

    results.receive(
        BaseResultEvents.resultRecord(
            BaseResultEvents.completed(secondEvent, secondSelection, SettlementResult.WON)),
        acknowledgment);

    var settled = bets.findWithSelectionsById(betId).orElseThrow();
    assertThat(settled.status()).isEqualTo(SettlementStatus.SETTLED);
    assertThat(settled.result()).isEqualTo(SettlementResult.WON);
    assertThat(settled.payout()).isEqualTo(Money.krw(400));
    assertThat(settled.selections()).allMatch(selection -> selection.sourceCandidateId() != null);
    verify(wallet)
        .credit(
            "settle:payout:" + betId, userId, Money.krw(300), WalletCreditPurpose.PROFIT_PAYOUT);
  }
}
