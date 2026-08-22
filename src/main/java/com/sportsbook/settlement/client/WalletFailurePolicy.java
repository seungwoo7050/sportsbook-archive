package com.sportsbook.settlement.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;

public final class WalletFailurePolicy {

  private static final ObjectMapper JSON = new ObjectMapper();

  private WalletFailurePolicy() {}

  public static void throwFor(ClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    String errorCode = readErrorCode(response, status);
    if (status == 429 || status >= 500) {
      throw new TransientFailure(status, errorCode);
    }
    throw new PermanentFailure(status, errorCode);
  }

  public static TransientFailure malformedSuccess() {
    return new TransientFailure(200, "WALLET_MALFORMED_RESPONSE");
  }

  private static String readErrorCode(ClientHttpResponse response, int status) throws IOException {
    try {
      WalletProblemDetail problem = JSON.readValue(response.getBody(), WalletProblemDetail.class);
      return problem.errorCode() == null || problem.errorCode().isBlank()
          ? "WALLET_HTTP_" + status
          : problem.errorCode();
    } catch (RuntimeException | IOException malformed) {
      return "WALLET_HTTP_" + status;
    }
  }

  public abstract static class Failure extends RuntimeException {
    private final int status;
    private final String errorCode;

    private Failure(int status, String errorCode) {
      super("wallet request failed: status=" + status + " errorCode=" + errorCode);
      this.status = status;
      this.errorCode = errorCode;
    }

    public int status() {
      return status;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  public static final class TransientFailure extends Failure {
    private TransientFailure(int status, String errorCode) {
      super(status, errorCode);
    }
  }

  public static final class PermanentFailure extends Failure {
    private PermanentFailure(int status, String errorCode) {
      super(status, errorCode);
    }
  }
}
