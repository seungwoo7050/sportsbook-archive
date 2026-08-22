package com.sportsbook.settlement.client;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
public class WalletHttpConfiguration {

  static final String REQUEST_FACTORY = "settlementWalletRequestFactory";

  @Bean(REQUEST_FACTORY)
  ClientHttpRequestFactory settlementWalletRequestFactory(WalletHttpProperties properties) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.readTimeout());
    return factory;
  }
}
