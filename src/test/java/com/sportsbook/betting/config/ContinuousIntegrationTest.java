package com.sportsbook.betting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContinuousIntegrationTest {

  @Test
  void pinsTheReleasedProtocolAndJavaToolchain() throws IOException {
    String workflow = Files.readString(Path.of(".github", "workflows", "verify.yml"));

    assertThat(workflow)
        .contains("f9de6bc1e533761ab4bb1454d8d4ab8175cdf001")
        .contains("java-version: \"17\"")
        .contains("./mvnw -B -DskipTests install")
        .contains("./mvnw -B clean verify");
  }
}
