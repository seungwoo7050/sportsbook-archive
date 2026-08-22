package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MavenWrapperTest {

  private static final String MAVEN_3_9_11 =
      "https://repo.maven.apache.org/maven2/org/apache/maven/"
          + "apache-maven/3.9.11/apache-maven-3.9.11-bin.zip";

  @Test
  void pinsTheApprovedMavenDistribution() throws IOException {
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(Path.of(".mvn/wrapper/maven-wrapper.properties"))) {
      properties.load(reader);
    }

    assertThat(properties)
        .containsEntry("wrapperVersion", "3.3.4")
        .containsEntry("distributionType", "only-script")
        .containsEntry("distributionUrl", MAVEN_3_9_11);
  }

  @Test
  void shipsExecutableUnixAndWindowsLaunchers() {
    Path unixLauncher = Path.of("mvnw");
    Path windowsLauncher = Path.of("mvnw.cmd");

    assertThat(unixLauncher).isRegularFile();
    assertThat(Files.isExecutable(unixLauncher)).isTrue();
    assertThat(windowsLauncher).isRegularFile();
  }
}
