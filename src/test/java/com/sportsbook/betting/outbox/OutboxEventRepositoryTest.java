package com.sportsbook.betting.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class OutboxEventRepositoryTest {

  @Test
  void ordersOnlyUnpublishedRowsByCreationTime() throws NoSuchMethodException {
    Method method = OutboxEventRepository.class.getMethod("findUnpublished", Pageable.class);

    assertThat(method.getAnnotation(Query.class).value())
        .isEqualTo(
            "select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc");
  }
}
