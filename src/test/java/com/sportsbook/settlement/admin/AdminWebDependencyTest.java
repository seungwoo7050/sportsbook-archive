package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminWebDependencyTest {

  @Test
  void usesTheBootServletStackForTheInternalApi() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(pom).contains("<artifactId>spring-boot-starter-web</artifactId>");
    assertThat(pom).doesNotContain("<artifactId>spring-web</artifactId>");
  }
}
