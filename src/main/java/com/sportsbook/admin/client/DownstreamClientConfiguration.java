package com.sportsbook.admin.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class DownstreamClientConfiguration {

  @Bean
  @Qualifier("walletRestClient")
  RestClient walletRestClient(
      RestClient.Builder builder,
      DownstreamProperties properties,
      DownstreamCredentials credentials) {
    return internalClient(
        builder, properties.walletBaseUrl().toString(), credentials.walletApiKey(), properties);
  }

  @Bean
  @Qualifier("riskRestClient")
  RestClient riskRestClient(
      RestClient.Builder builder,
      DownstreamProperties properties,
      DownstreamCredentials credentials) {
    return internalClient(
        builder, properties.riskBaseUrl().toString(), credentials.riskApiKey(), properties);
  }

  private static RestClient internalClient(
      RestClient.Builder builder,
      String baseUrl,
      String apiKey,
      DownstreamProperties properties) {
    SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(properties.connectTimeout());
    requests.setReadTimeout(properties.readTimeout());
    return builder
        .clone()
        .baseUrl(baseUrl)
        .requestFactory(requests)
        .defaultHeader(DownstreamHeaders.INTERNAL_SERVICE, DownstreamHeaders.ADMIN_API)
        .defaultHeader(DownstreamHeaders.INTERNAL_API_KEY, apiKey)
        .build();
  }
}
