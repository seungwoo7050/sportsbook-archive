package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FormattingConfigurationTest {

  @Test
  void checksProductionAndTestJavaWithThePinnedFormatter() throws IOException {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(pom)
        .contains("<artifactId>spotless-maven-plugin</artifactId>")
        .contains("<version>${spotless.version}</version>")
        .contains("<include>src/main/java/**/*.java</include>")
        .contains("<include>src/test/java/**/*.java</include>")
        .contains("<version>1.22.0</version>")
        .contains("<style>GOOGLE</style>")
        .contains("<goal>check</goal>");
  }
}
