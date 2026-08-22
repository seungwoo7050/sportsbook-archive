package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MarketClient {

  private final RestClient http;
  private final DownstreamFailureMapper failures = new DownstreamFailureMapper();

  public MarketClient(@Qualifier("oddsRestClient") RestClient http) {
    this.http = http;
  }

  public void changeStatus(
      UUID eventId,
      UUID marketId,
      Action action,
      MarketStatusPayload body,
      IdempotencyKey idempotencyKey,
      UUID adminActionId) {
    var response =
        failures.execute(
            () ->
                http.post()
                    .uri(
                        builder ->
                            builder
                                .pathSegment("internal", "v1", "events")
                                .pathSegment(eventId.toString(), "markets", marketId.toString())
                                .pathSegment(action.wireValue)
                                .build())
                    .header("Idempotency-Key", idempotencyKey.value())
                    .header(DownstreamHeaders.ADMIN_ACTION_ID, adminActionId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class));
    DownstreamContract.requireEmpty(
        response, HttpStatus.ACCEPTED, "Odds market action must return empty HTTP 202");
  }

  public enum Action {
    SUSPEND("suspend"),
    CLOSE("close"),
    REOPEN("reopen");

    private final String wireValue;

    Action(String wireValue) {
      this.wireValue = wireValue;
    }
  }
}
