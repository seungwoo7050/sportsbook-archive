package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sportsbook.admin.client.DownstreamContract;
import com.sportsbook.admin.client.DownstreamContractException;
import com.sportsbook.admin.client.DownstreamFailureMapper;
import com.sportsbook.admin.client.DownstreamUnavailableException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;

class DownstreamUnknownResponseTest {

  @Test
  void mapsTimeoutsToAnOpaqueGatewayTimeout() {
    DownstreamUnavailableException failure =
        catchThrowableOfType(
            () ->
                new DownstreamFailureMapper()
                    .execute(
                        () -> {
                          throw new ResourceAccessException(
                              "read timed out", new SocketTimeoutException("secret host"));
                        }),
            DownstreamUnavailableException.class);

    var response =
        new AdminExceptionHandler().downstreamUnavailable(failure, request("/admin/v1/test"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody().errorCode()).isEqualTo("GATEWAY_TIMEOUT");
    assertThat(response.getBody().detail()).doesNotContain("secret host");
  }

  @Test
  void mapsMalformedSuccessToAnOpaqueBadGateway() {
    DownstreamContractException failure =
        catchThrowableOfType(
            () ->
                DownstreamContract.requireBody(
                    ResponseEntity.ok().build(),
                    HttpStatus.OK,
                    ignored -> true,
                    "secret contract detail"),
            DownstreamContractException.class);

    var response =
        new AdminExceptionHandler().downstreamContract(failure, request("/admin/v1/test"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody().errorCode()).isEqualTo("DOWNSTREAM_CONTRACT_VIOLATION");
    assertThat(response.getBody().detail()).doesNotContain("secret contract detail");
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(path);
    return request;
  }
}
