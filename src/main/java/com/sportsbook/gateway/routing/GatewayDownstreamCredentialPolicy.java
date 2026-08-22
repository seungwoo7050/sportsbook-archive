package com.sportsbook.gateway.routing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
  BettingDownstreamProperties.class,
  WalletDownstreamProperties.class
})
class GatewayDownstreamCredentialPolicy {

  GatewayDownstreamCredentialPolicy(
      BettingDownstreamProperties betting, WalletDownstreamProperties wallet) {
    if (betting.requiredApiKey().equals(wallet.requiredApiKey())) {
      throw new IllegalArgumentException("Gateway downstream API keys must be distinct");
    }
  }
}
