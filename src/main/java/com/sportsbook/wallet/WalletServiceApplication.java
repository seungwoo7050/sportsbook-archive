package com.sportsbook.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class WalletServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WalletServiceApplication.class, args);
  }
}
