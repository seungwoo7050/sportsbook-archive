package com.sportsbook.wallet.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.service.WalletService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformTransactionControllerRejectionTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000443");
  private static final String BODY =
      "{\"userId\":\"" + USER_ID + "\",\"amount\":{\"amount\":1,\"currency\":\"KRW\"}}";

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new PlatformTransactionController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void rejectsMissingDuplicateAndBlankIdempotencyKeys() throws Exception {
    assertInvalid(
        mvc.perform(
            post("/internal/v1/wallet/transactions/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)));
    assertInvalid(
        mvc.perform(
            post("/internal/v1/wallet/transactions/withdraw")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "first", "second")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)));
    assertInvalid(
        mvc.perform(
            post("/internal/v1/wallet/transactions/deposit")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, " ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)));
    verifyNoInteractions(wallet);
  }

  @Test
  void rejectsMissingAndNonPositiveTransactionFields() throws Exception {
    assertInvalid(
        mvc.perform(
            post("/internal/v1/wallet/transactions/deposit")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "deposit:invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")));
    assertInvalid(
        mvc.perform(
            post("/internal/v1/wallet/transactions/withdraw")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "withdraw:invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\""
                        + USER_ID
                        + "\",\"amount\":{\"amount\":0,\"currency\":\"KRW\"}}")));
    verifyNoInteractions(wallet);
  }

  private void assertInvalid(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
  }
}
