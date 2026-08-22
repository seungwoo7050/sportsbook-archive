package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettlementCiWorkflowTest {

  @Test
  void pinsTheContractAndRunsOneFreshJava17Verification() throws IOException {
    String workflow = Files.readString(Path.of(".github/workflows/settlement-ci.yml"));

    assertThat(workflow)
        .contains(
            "distribution: temurin",
            "java-version: \"17\"",
            "ref: f9de6bc1e533761ab4bb1454d8d4ab8175cdf001",
            "-Dmaven.repo.local=${{ runner.temp }}/settlement-m2",
            "working-directory: shared-protocol",
            "working-directory: settlement-service",
            "-DskipTests install",
            "clean verify")
        .containsOnlyOnce("clean verify")
        .doesNotContain("ref: shared-protocol", "java-version: \"21\"");
  }
}
