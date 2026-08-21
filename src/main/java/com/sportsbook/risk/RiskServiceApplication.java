package com.sportsbook.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@SuppressWarnings("HideUtilityClassConstructor")
public class RiskServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(RiskServiceApplication.class, args);
  }
}
