package com.sportsbook.betting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

class BetRepositoryContractTest {

  @Test
  void locksAggregateBeforeSagaOrResolutionTransition() throws Exception {
    var method = BetRepository.class.getMethod("findLockedByBetId", UUID.class);

    assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    assertThat(method.getAnnotation(EntityGraph.class).attributePaths()).containsExactly("legs");
  }
}
