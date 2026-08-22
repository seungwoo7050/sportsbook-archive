package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.betting.error.InsufficientBalanceException;
import com.sportsbook.betting.error.WalletRejectedException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

class WalletProblemMapperTest {

  private final WalletProblemMapper mapper = new WalletProblemMapper(new ObjectMapper());

  @Test
  void mapsStoredBusinessProblemsWithoutChangingTheirMeaning() {
    WalletProblem insufficient = read("WALLET_INSUFFICIENT_BALANCE", "not enough balance");
    WalletProblem missing = read("WALLET_ACCOUNT_NOT_FOUND", "account missing");

    assertThat(mapper.map(insufficient)).isInstanceOf(InsufficientBalanceException.class);
    assertThat(mapper.map(missing))
        .isInstanceOf(WalletRejectedException.class)
        .extracting("walletErrorCode")
        .isEqualTo("WALLET_ACCOUNT_NOT_FOUND");

    for (String code :
        java.util.List.of(
            "WALLET_CURRENCY_MISMATCH",
            "WALLET_AMOUNT_OUT_OF_RANGE",
            "WALLET_ACCOUNT_RECOVERY_BLOCKED")) {
      assertThat(mapper.map(read(code, "durable rejection")))
          .isInstanceOf(WalletRejectedException.class)
          .extracting("walletErrorCode")
          .isEqualTo(code);
    }
    assertThat(mapper.map(read("WALLET_IDEMPOTENCY_CONFLICT", "conflict")))
        .isInstanceOf(com.sportsbook.betting.error.DependencyUnavailableException.class);
  }

  private WalletProblem read(String code, String detail) {
    String json = "{\"errorCode\":\"" + code + "\",\"detail\":\"" + detail + "\"}";
    return mapper.read(
        new MockClientHttpResponse(
            json.getBytes(StandardCharsets.UTF_8), HttpStatus.UNPROCESSABLE_ENTITY));
  }
}
