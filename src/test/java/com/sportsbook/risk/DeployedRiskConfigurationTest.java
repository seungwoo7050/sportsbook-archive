package com.sportsbook.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.RiskHistoryProperties;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class DeployedRiskConfigurationTest {
  @Test
  void bindsEveryDeployedRiskPolicy() throws IOException {
    PropertySource<?> source =
        new YamlPropertySourceLoader()
            .load("risk-defaults", new ClassPathResource("application.yml"))
            .get(0);
    Binder binder = new Binder(ConfigurationPropertySources.from(source));

    RiskLimitProperties limits = bind(binder, "risk.limits", RiskLimitProperties.class);
    RiskPatternProperties patterns = bind(binder, "risk.patterns", RiskPatternProperties.class);
    RiskReservationProperties reservations =
        bind(binder, "risk.reservations", RiskReservationProperties.class);
    RiskHistoryProperties history = bind(binder, "risk.history", RiskHistoryProperties.class);

    assertThat(limits.limit(LimitType.STAKE_DAILY, Currency.USD)).isEqualTo(100_000L);
    assertThat(patterns.rapidBetting().action()).isEqualTo(PatternAction.SUSPECT);
    assertThat(patterns.repeatedSelection().action()).isEqualTo(PatternAction.REVIEW);
    assertThat(reservations.retention()).isEqualTo(Duration.ofDays(32));
    assertThat(history.maxStakeSamples()).isEqualTo(100);
    assertThat(source.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,redis,kafka");
  }

  private static <T> T bind(Binder binder, String prefix, Class<T> type) {
    return binder
        .bind(prefix, Bindable.of(type))
        .orElseThrow(() -> new AssertionError("Missing deployed configuration: " + prefix));
  }
}
