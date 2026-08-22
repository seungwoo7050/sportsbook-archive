package com.sportsbook.settlement.config;

import com.sportsbook.settlement.admin.AdminCredentials;
import com.sportsbook.settlement.client.WalletCredentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public final class SettlementCredentialPolicy {

  public SettlementCredentialPolicy(
      AdminCredentials adminCredentials, WalletCredentials walletCredentials) {
    byte[] admin = adminCredentials.apiKey().getBytes(StandardCharsets.UTF_8);
    byte[] wallet = walletCredentials.apiKey().getBytes(StandardCharsets.UTF_8);
    if (MessageDigest.isEqual(admin, wallet)) {
      throw new IllegalArgumentException(
          "Settlement admin and Wallet credentials must be distinct");
    }
  }
}
