package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class PostgresSchemaIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private EntityManagerFactory entities;
  @Autowired private Environment environment;
  @Autowired private Flyway flyway;

  @Test
  void migratesAndValidatesTheProductionSchema() {
    assertThat(entities.isOpen()).isTrue();
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(flyway.info().pending()).isEmpty();
  }
}
