package com.sportsbook.wallet.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.service.WalletService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BettingDebitRejectionTest {
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000454");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000455");
  private static final String BODY =
      "{\"userId\":\"" + USER_ID + "\",\"amount\":{\"amount\":1,\"currency\":\"KRW\"}}";

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new BettingTransactionController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void rejectsMissingDuplicateAndNoncanonicalBetKeys() throws Exception {
    assertInvalid(debit());
    assertInvalid(
        debit().header(WalletRequestHeaders.IDEMPOTENCY_KEY, BET_ID.toString(), BET_ID.toString()));
    for (String invalid :
        List.of(BET_ID.toString().toUpperCase(Locale.ROOT), "1-1-1-1-1", "not-a-bet-id")) {
      assertInvalid(debit().header(WalletRequestHeaders.IDEMPOTENCY_KEY, invalid));
    }
    verifyNoInteractions(wallet);
  }

  @Test
  void rejectsInvalidDebitBodiesBeforeCallingTheService() throws Exception {
    assertInvalid(
        post("/internal/v1/wallet/transactions/debit")
            .header(WalletRequestHeaders.IDEMPOTENCY_KEY, BET_ID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"));
    assertInvalid(
        post("/internal/v1/wallet/transactions/debit")
            .header(WalletRequestHeaders.IDEMPOTENCY_KEY, BET_ID.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"userId\":\"" + USER_ID + "\",\"amount\":{\"amount\":0,\"currency\":\"KRW\"}}"));
    verifyNoInteractions(wallet);
  }

  private MockHttpServletRequestBuilder debit() {
    return post("/internal/v1/wallet/transactions/debit")
        .contentType(MediaType.APPLICATION_JSON)
        .content(BODY);
  }

  private void assertInvalid(MockHttpServletRequestBuilder request) throws Exception {
    assertInvalid(mvc.perform(request));
  }

  private void assertInvalid(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
  }
}
