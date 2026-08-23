package com.sportsbook.betting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

class BetRepositoryContractTest {

  @Test
  void locksTheRootBeforeLoadingTheAggregateGraph() throws Exception {
    var aggregate = BetRepository.class.getMethod("findLockedByBetId", UUID.class);
    var root = BetRepository.class.getMethod("findLockedRootByBetId", UUID.class);

    assertThat(aggregate.isDefault()).isTrue();
    assertThat(root.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(root.getAnnotation(EntityGraph.class)).isNull();
  }
}
