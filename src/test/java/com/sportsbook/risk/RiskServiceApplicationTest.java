package com.sportsbook.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

class RiskServiceApplicationTest {
  @Test
  void exposesAnExecutableSpringBootApplication() throws NoSuchMethodException {
    assertThat(RiskServiceApplication.class).hasAnnotation(SpringBootApplication.class);
    assertThat(RiskServiceApplication.class).hasAnnotation(ConfigurationPropertiesScan.class);

    var main = RiskServiceApplication.class.getDeclaredMethod("main", String[].class);
    assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
    assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
  }
}
