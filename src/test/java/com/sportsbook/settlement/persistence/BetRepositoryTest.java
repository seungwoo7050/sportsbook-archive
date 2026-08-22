package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create", "spring.flyway.enabled=false"})
class BetRepositoryTest {

  @Autowired private BetRepository repository;

  @Test
  @Transactional
  void queriesAndLocksPendingBetsInStableOrder() throws NoSuchMethodException {
    UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    repository.saveAllAndFlush(List.of(pending(secondId, eventId), pending(firstId, eventId)));

    assertThat(repository.findPendingIdsByEvent(eventId)).containsExactly(firstId, secondId);
    assertThat(repository.findForUpdateById(firstId)).isPresent();
    assertThat(repository.findAllWithSelectionsByIdIn(List.of(secondId, firstId)))
        .extracting(Bet::betId)
        .containsExactly(firstId, secondId);
    assertThat(repository.findWithSelectionsById(firstId).orElseThrow().selections()).hasSize(1);

    Query lockQuery =
        BetRepository.class.getMethod("lockPendingIds", List.class).getAnnotation(Query.class);
    assertThat(lockQuery.nativeQuery()).isTrue();
    assertThat(lockQuery.value()).contains("order by bet_id for update");
  }

  private static Bet pending(UUID betId, UUID eventId) {
    BetSelection leg =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    return Bet.pending(
        betId,
        UUID.randomUUID(),
        SlipKind.SINGLE,
        null,
        null,
        new EmbeddedMoney(100, Currency.KRW),
        Instant.EPOCH,
        List.of(leg),
        Instant.EPOCH);
  }
}
