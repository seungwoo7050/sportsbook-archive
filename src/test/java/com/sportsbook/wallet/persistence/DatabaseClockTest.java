package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(DatabaseClock.class)
class DatabaseClockTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired DatabaseClock databaseClock;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void readsThePostgreSqlWallClockInsideTheTransaction() {
    Instant before = Instant.now().minusSeconds(1L);
    Instant databaseNow =
        new TransactionTemplate(transactions).execute(ignored -> databaseClock.now());

    assertThat(databaseNow).isAfter(before).isBefore(Instant.now().plusSeconds(1L));
  }

  @Test
  void refusesAReplicaLocalTimestampOutsideTheTransaction() {
    assertThatThrownBy(databaseClock::now)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Database clock requires an active transaction");
  }
}
