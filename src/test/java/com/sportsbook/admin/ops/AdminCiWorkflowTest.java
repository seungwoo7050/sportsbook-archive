package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminCiWorkflowTest {

  @Test
  void pinsSharedAndRunsOneFreshJava17Verification() throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/admin-ci.yml"));

    assertThat(workflow)
        .contains(
            "fetch-depth: 0",
            "github.event.pull_request.head.sha",
            "distribution: temurin",
            "java-version: \"17\"",
            "ref: f9de6bc1e533761ab4bb1454d8d4ab8175cdf001",
            "working-directory: shared-protocol",
            "working-directory: admin-api",
            "-Dmaven.repo.local=${{ runner.temp }}/admin-m2",
            "-DskipTests install",
            "bash .github/scripts/verify-history.sh",
            "clean verify")
        .containsOnlyOnce("clean verify")
        .doesNotContain(
            "ref: shared-protocol",
            "sportsbook-shared-protocol",
            "sportsbook-v2.0.0",
            "java-version: \"21\"",
            "clean package",
            "mvn test");
    assertThat(workflow.indexOf("-DskipTests install"))
        .isLessThan(workflow.indexOf("clean verify"));
    assertThat(workflow.indexOf("bash .github/scripts/verify-history.sh"))
        .isLessThan(workflow.indexOf("clean verify"));
  }
}
