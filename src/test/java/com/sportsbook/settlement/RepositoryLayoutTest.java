package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RepositoryLayoutTest {

  @Test
  void ignoresGeneratedBuildOutput() throws Exception {
    assertThat(Files.readAllLines(Path.of(".gitignore"))).contains("target/");
  }

  @Test
  void keepsQualityGatesInTheBuild() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom)
        .contains("spotless-maven-plugin")
        .contains("maven-checkstyle-plugin")
        .contains("<phase>verify</phase>");
    assertThat(Path.of("config/checkstyle/checkstyle.xml")).exists();
  }
}
