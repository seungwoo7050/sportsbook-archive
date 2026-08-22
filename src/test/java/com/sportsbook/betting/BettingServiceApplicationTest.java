package com.sportsbook.betting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

class BettingServiceApplicationTest {

  @Test
  void enablesConfigurationScanningAndScheduling() {
    Class<BettingServiceApplication> application = BettingServiceApplication.class;

    assertThat(application).hasAnnotation(SpringBootApplication.class);
    assertThat(application).hasAnnotation(ConfigurationPropertiesScan.class);
    assertThat(application).hasAnnotation(EnableScheduling.class);
  }
}
