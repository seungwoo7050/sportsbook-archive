package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BuildBaselineTest {

  @Test
  void targetsJava17AndLinksSharedProtocol() {
    assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(17);
    assertThat(Money.class.getPackageName()).isEqualTo("com.sportsbook.protocol.value");
  }

  @Test
  void packagesTheServiceAsAnExecutableBootJar() throws IOException {
    assertThat(Files.readString(Path.of("pom.xml")))
        .contains("<artifactId>spring-boot-maven-plugin</artifactId>");
  }
}
