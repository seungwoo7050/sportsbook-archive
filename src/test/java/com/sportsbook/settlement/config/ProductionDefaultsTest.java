package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class ProductionDefaultsTest {

  @Test
  void validatesJpaMappingsAgainstTheMigratedSchema() throws IOException {
    MutablePropertySources sources = new MutablePropertySources();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .forEach(sources::addLast);

    assertThat(
            new PropertySourcesPropertyResolver(sources)
                .getProperty("spring.jpa.hibernate.ddl-auto"))
        .isEqualTo("validate");
  }
}
