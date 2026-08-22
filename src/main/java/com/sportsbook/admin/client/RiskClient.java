package com.sportsbook.admin.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RiskClient {

  private final RestClient http;
  private final DownstreamFailureMapper failures = new DownstreamFailureMapper();

  public RiskClient(@Qualifier("riskRestClient") RestClient http) {
    this.http = http;
  }

  public RiskLimitsResponse getLimits(UUID userId) {
    var response =
        failures.execute(
            () ->
                http.get()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "v1", "risk", "limits")
                                .pathSegment(userId.toString())
                                .build())
                    .retrieve()
                    .toEntity(RiskLimitsResponse.class));
    RiskLimitsResponse body =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Risk limits GET must return HTTP 200 with a body");
    return RiskLimitsResponse.verify(userId, body);
  }

  public void setLimit(UUID userId, RiskLimitPayload limit) {
    var response =
        failures.execute(
            () ->
                http.patch()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "v1", "risk", "limits")
                                .pathSegment(userId.toString())
                                .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(limit)
                    .retrieve()
                    .toEntity(byte[].class));
    DownstreamContract.requireEmpty(
        response, HttpStatus.NO_CONTENT, "Risk limit PATCH must return empty HTTP 204");
  }
}
