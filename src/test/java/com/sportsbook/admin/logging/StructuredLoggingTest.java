package com.sportsbook.admin.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = StructuredLoggingTest.LoggingApplication.class,
    properties = "management.endpoint.health.group.readiness.include=readinessState",
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StructuredLoggingTest {

  @Test
  void fixesStructuredLoggerLevels() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.INFO);
    assertThat(context.getLogger("com.sportsbook.admin").getLevel()).isEqualTo(Level.INFO);
    assertThat(context.getLogger("org.apache.kafka").getLevel()).isEqualTo(Level.WARN);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  static class LoggingApplication {}
}
