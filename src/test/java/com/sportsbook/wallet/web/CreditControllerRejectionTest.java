package com.sportsbook.wallet.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.security.InternalApiKeyAuthenticationFilter;
import com.sportsbook.wallet.security.TestInternalApiKeys;
import com.sportsbook.wallet.security.WalletSecurityConfig;
import com.sportsbook.wallet.service.WalletService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CreditController.class)
@Import(WalletSecurityConfig.class)
class CreditControllerRejectionTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000473");
  private static final String VALID_BODY =
      "{\"userId\":\""
          + USER_ID
          + "\",\"amount\":{\"amount\":100,\"currency\":\"KRW\"},"
          + "\"source\":\"HOUSE_POOL\",\"reason\":\"PAYOUT\"}";

  @Autowired private MockMvc mvc;
  @MockBean private WalletService wallet;

  @DynamicPropertySource
  static void securityProperties(DynamicPropertyRegistry registry) {
    TestInternalApiKeys.register(registry);
  }

  @Test
  void rejectsMissingAndDuplicateCreditKeysBeforeCallingTheService() throws Exception {
    assertInvalid(credit().content(VALID_BODY));
    assertInvalid(
        credit()
            .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "credit:first", "credit:second")
            .content(VALID_BODY));
    verifyNoInteractions(wallet);
  }

  @Test
  void rejectsMissingCreditSemanticsBeforeCallingTheService() throws Exception {
    assertInvalid(
        credit()
            .header(WalletRequestHeaders.IDEMPOTENCY_KEY, "credit:missing-reason")
            .content(
                "{\"userId\":\""
                    + USER_ID
                    + "\",\"amount\":{\"amount\":100,\"currency\":\"KRW\"},"
                    + "\"source\":\"HOUSE_POOL\"}"));
    verifyNoInteractions(wallet);
  }

  private MockHttpServletRequestBuilder credit() {
    return post("/internal/v1/wallet/transactions/credit")
        .header(
            InternalApiKeyAuthenticationFilter.SERVICE_HEADER, WalletCaller.SETTLEMENT.wireName())
        .header(
            InternalApiKeyAuthenticationFilter.API_KEY_HEADER,
            TestInternalApiKeys.key(WalletCaller.SETTLEMENT))
        .contentType(MediaType.APPLICATION_JSON);
  }

  private void assertInvalid(MockHttpServletRequestBuilder request) throws Exception {
    mvc.perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
  }
}
