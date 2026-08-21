package com.sportsbook.wallet.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.service.WalletAdjustmentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdjustmentControllerPayoutRejectionTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004e1");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004e2");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004e3");

  private final WalletAdjustmentService adjustments = mock(WalletAdjustmentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AdjustmentController(adjustments))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void rejectsZeroAndNegativePayoutSnapshots() throws Exception {
    assertInvalid(mvc.perform(request(body(700L, "KRW", 700L, "KRW"))));
    assertInvalid(mvc.perform(request(body(-1L, "KRW", 700L, "KRW"))));
    assertInvalid(mvc.perform(request(body(700L, "KRW", -1L, "KRW"))));
    verifyNoInteractions(adjustments);
  }

  @Test
  void rejectsMixedCurrenciesAndMissingPayouts() throws Exception {
    assertInvalid(mvc.perform(request(body(700L, "KRW", 1_000L, "USD"))));
    assertInvalid(
        mvc.perform(
            request(
                "{\"revisionId\":\""
                    + REVISION_ID
                    + "\",\"betId\":\""
                    + BET_ID
                    + "\",\"revisionNumber\":2,\"userId\":\""
                    + USER_ID
                    + "\"}")));
    verifyNoInteractions(adjustments);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      String body) {
    return post("/internal/v1/wallet/transactions/adjustment")
        .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "settlement:revision:" + REVISION_ID)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String body(long previous, String previousCurrency, long next, String nextCurrency) {
    return "{\"revisionId\":\""
        + REVISION_ID
        + "\",\"betId\":\""
        + BET_ID
        + "\",\"revisionNumber\":2,\"userId\":\""
        + USER_ID
        + "\",\"previousPayout\":{\"amount\":"
        + previous
        + ",\"currency\":\""
        + previousCurrency
        + "\"},\"newPayout\":{\"amount\":"
        + next
        + ",\"currency\":\""
        + nextCurrency
        + "\"}}";
  }

  private void assertInvalid(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
  }
}
