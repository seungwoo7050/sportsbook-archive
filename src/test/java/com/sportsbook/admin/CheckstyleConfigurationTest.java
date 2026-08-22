package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CheckstyleConfigurationTest {

  @Test
  void enablesTheApprovedSemanticRuleSetForProductionSources() throws IOException {
    String rules = Files.readString(Path.of("config/checkstyle/checkstyle.xml"));
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(rules)
        .contains("<module name=\"MagicNumber\">")
        .contains("<module name=\"ParameterNumber\">")
        .contains("<module name=\"UnusedImports\"/>")
        .contains("<module name=\"RedundantImport\"/>")
        .contains("<module name=\"EmptyBlock\">")
        .contains("<module name=\"HideUtilityClassConstructor\"/>");
    assertThat(pom)
        .contains("<artifactId>maven-checkstyle-plugin</artifactId>")
        .contains("<includeTestSourceDirectory>false</includeTestSourceDirectory>")
        .contains("<phase>verify</phase>");
  }
}
