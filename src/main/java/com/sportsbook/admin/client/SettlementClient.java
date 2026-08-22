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

  public SettlementCandidateReceipt approveCandidate(UUID candidateId, UUID idempotencyKey) {
    var response =
        failures.execute(
            () ->
                http.post()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "admin", "result-candidates")
                                .pathSegment(candidateId.toString(), "approve")
                                .build())
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .retrieve()
                    .toEntity(SettlementCandidateReceipt.class));
    SettlementCandidateReceipt receipt =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Settlement candidate approval must return HTTP 200 with a receipt");
    return SettlementCandidateReceipt.verify(
        idempotencyKey, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, receipt);
  }

  public SettlementCandidateReceipt rejectCandidate(
      UUID candidateId, UUID idempotencyKey, SettlementRejectionPayload payload) {
    var response =
        failures.execute(
            () ->
                http.post()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "admin", "result-candidates")
                                .pathSegment(candidateId.toString(), "reject")
                                .build())
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .body(payload)
                    .retrieve()
                    .toEntity(SettlementCandidateReceipt.class));
    SettlementCandidateReceipt receipt =
        DownstreamContract.requireBody(
            response,
            HttpStatus.OK,
            ignored -> true,
            "Settlement candidate rejection must return HTTP 200 with a receipt");
    return SettlementCandidateReceipt.verify(
        idempotencyKey, SettlementCandidateReceipt.Outcome.CANDIDATE_REJECTED, receipt);
  }

  public SettlementRetryReceipt retryRevision(UUID revisionId, UUID idempotencyKey) {
    var response =
        failures.execute(
            () ->
                http.post()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "admin", "revisions")
                                .pathSegment(revisionId.toString(), "retry")
                                .build())
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .retrieve()
                    .toEntity(SettlementRetryReceipt.class));
    SettlementRetryReceipt receipt =
        DownstreamContract.requireBody(
            response,
            HttpStatus.ACCEPTED,
            ignored -> true,
            "Settlement revision retry must return HTTP 202 with a receipt");
    return SettlementRetryReceipt.verify(idempotencyKey, receipt);
  }
}
