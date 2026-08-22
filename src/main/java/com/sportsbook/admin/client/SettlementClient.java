package com.sportsbook.admin.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SettlementClient {

  private final RestClient http;
  private final DownstreamFailureMapper failures = new DownstreamFailureMapper();

  public SettlementClient(@Qualifier("settlementRestClient") RestClient http) {
    this.http = http;
  }

  public SettlementCandidateView getCandidate(UUID candidateId) {
    var response =
        failures.execute(
            () ->
                http.get()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "admin", "result-candidates")
                                .pathSegment(candidateId.toString())
                                .build())
                    .retrieve()
                    .toEntity(SettlementCandidateView.class));
    SettlementCandidateView body =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Settlement candidate GET must return HTTP 200 with a body");
    return SettlementCandidateView.verify(candidateId, body);
  }

  public SettlementRevisionView getRevision(UUID revisionId) {
    var response =
        failures.execute(
            () ->
                http.get()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "admin", "revisions")
                                .pathSegment(revisionId.toString())
                                .build())
                    .retrieve()
                    .toEntity(SettlementRevisionView.class));
    SettlementRevisionView body =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Settlement revision GET must return HTTP 200 with a body");
    return SettlementRevisionProof.verify(revisionId, body);
  }
}
