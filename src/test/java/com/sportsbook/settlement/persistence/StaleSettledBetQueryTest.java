package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create", "spring.flyway.enabled=false"})
class StaleSettledBetQueryTest {

  @Autowired private BetRepository repository;

  @Test
  void returnsOnlySettledBetsResolvedFromAnotherCandidate() {
    UUID eventId = UUID.randomUUID();
    UUID accepted = UUID.randomUUID();
    UUID staleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID laterStaleId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    repository.save(settled(staleId, eventId, UUID.randomUUID()));
    repository.save(settled(laterStaleId, eventId, UUID.randomUUID()));
    repository.save(settled(UUID.randomUUID(), eventId, accepted));
    repository.save(pending(UUID.randomUUID(), eventId, accepted));
    repository.flush();

    assertThat(repository.findStaleSettledIdsByEvent(eventId, accepted, PageRequest.of(0, 1)))
        .containsExactly(staleId);
  }

  private static Bet settled(UUID betId, UUID eventId, UUID candidateId) {
    Bet bet = pending(betId, eventId, candidateId);
    bet.recordSettled(SettlementResult.WON, Money.krw(200), Instant.EPOCH);
    return bet;
  }

  private static Bet pending(UUID betId, UUID eventId, UUID candidateId) {
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    selection.applyCandidate(candidateId, SettlementResult.WON);
    return Bet.pending(
        betId,
        UUID.randomUUID(),
        SlipKind.SINGLE,
        null,
        null,
        EmbeddedMoney.of(Money.krw(100)),
        Instant.EPOCH,
        List.of(selection),
        Instant.EPOCH);
  }
}
