package com.sportsbook.betting.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

  @Bean
  RestClient riskRestClient(RestClient.Builder builder, ClientProperties properties) {
    return client(builder, properties.riskBaseUrl(), properties.riskApiKey(), properties);
  }

  @Bean
  RestClient walletRestClient(RestClient.Builder builder, ClientProperties properties) {
    return client(builder, properties.walletBaseUrl(), properties.walletApiKey(), properties);
  }

  private static RestClient client(
      RestClient.Builder builder, String baseUrl, String apiKey, ClientProperties properties) {
    return client(builder, baseUrl, apiKey, requestFactory(properties));
  }

  static RestClient client(
      RestClient.Builder builder,
      String baseUrl,
      String apiKey,
      ClientHttpRequestFactory requestFactory) {
    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(new InternalClientHeaders(apiKey))
        .build();
  }

  private static ClientHttpRequestFactory requestFactory(ClientProperties properties) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());
    return ClientHttpRequestFactories.get(settings);
  }
}
