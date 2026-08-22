package com.sportsbook.settlement.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public final class WalletAuthenticationHeaders {

  public static final String SERVICE_HEADER = "X-Internal-Service";
  public static final String API_KEY_HEADER = "X-Internal-Api-Key";

  private final WalletCredentials credentials;

  public WalletAuthenticationHeaders(WalletCredentials credentials) {
    this.credentials = credentials;
  }

  public void apply(HttpHeaders headers) {
    headers.set(SERVICE_HEADER, WalletCredentials.CALLER);
    headers.set(API_KEY_HEADER, credentials.apiKey());
  }
}
