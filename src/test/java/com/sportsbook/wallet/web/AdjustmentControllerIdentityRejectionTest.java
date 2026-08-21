package com.sportsbook.wallet.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdjustmentControllerIdentityRejectionTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004d1");
  private static final UUID OTHER_REVISION_ID =
      UUID.fromString("019b76da-a000-7000-8000-0000000004d2");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004d3");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004d4");

  private final WalletAdjustmentService adjustments = mock(WalletAdjustmentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AdjustmentController(adjustments))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void rejectsMissingDuplicateAndMismatchedRevisionKeys() throws Exception {
    assertInvalid(mvc.perform(request(body(USER_ID, 2L))));
    assertInvalid(
        mvc.perform(
            request(body(USER_ID, 2L))
                .header(
                    WalletRequestHeaders.IDEMPOTENCY_KEY,
                    key(REVISION_ID),
                    key(OTHER_REVISION_ID))));
    assertInvalid(
        mvc.perform(
            request(body(USER_ID, 2L))
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, key(OTHER_REVISION_ID))));
    verifyNoInteractions(adjustments);
  }

  @Test
  void rejectsReservedAccountsAndInvalidRevisionNumbers() throws Exception {
    assertInvalid(
        mvc.perform(
            request(body(SystemAccountIds.HOUSE, 2L))
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, key(REVISION_ID))));
    assertInvalid(
        mvc.perform(
            request(body(USER_ID, 0L))
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, key(REVISION_ID))));
    verifyNoInteractions(adjustments);
  }

  private MockHttpServletRequestBuilder request(String body) {
    return post("/internal/v1/wallet/transactions/adjustment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String body(UUID userId, long revisionNumber) {
    return "{\"revisionId\":\""
        + REVISION_ID
        + "\",\"betId\":\""
        + BET_ID
        + "\",\"revisionNumber\":"
        + revisionNumber
        + ",\"userId\":\""
        + userId
        + "\",\"previousPayout\":{\"amount\":700,\"currency\":\"KRW\"},"
        + "\"newPayout\":{\"amount\":1000,\"currency\":\"KRW\"}}";
  }

  private String key(UUID revisionId) {
    return "settlement:revision:" + revisionId;
  }

  private void assertInvalid(ResultActions result) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
  }
}
