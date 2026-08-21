package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.security.InternalApiKeyAuthenticationFilter;
import com.sportsbook.wallet.security.TestInternalApiKeys;
import com.sportsbook.wallet.security.WalletSecurityConfig;
import com.sportsbook.wallet.service.WalletOperationResult;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.time.Instant;
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

@WebMvcTest(CreditController.class)
@Import(WalletSecurityConfig.class)
class CreditControllerAuthenticationTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000471");

  @Autowired private MockMvc mvc;
  @MockBean private WalletService wallet;

  @DynamicPropertySource
  static void securityProperties(DynamicPropertyRegistry registry) {
    TestInternalApiKeys.register(registry);
  }

  @Test
  void passesEveryAuthorizedPrincipalToTheCreditService() throws Exception {
    when(wallet.credit(any(), any()))
        .thenReturn(
            new WalletOperationResult(
                UUID.fromString("019b76da-a000-7000-8000-000000000472"),
                USER_ID,
                Money.krw(100),
                LedgerReason.BET_REFUND,
                Instant.parse("2026-08-21T20:00:00Z")));

    assertCredit(
        WalletCaller.BETTING,
        CreditCommand.Source.USER_LOCKED,
        CreditReason.REFUND,
        "credit:betting");
    assertCredit(
        WalletCaller.SETTLEMENT,
        CreditCommand.Source.HOUSE_POOL,
        CreditReason.PAYOUT,
        "credit:settlement");
    assertCredit(
        WalletCaller.ADMIN, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND, "credit:admin");
  }

  private void assertCredit(
      WalletCaller caller, CreditCommand.Source source, CreditReason reason, String rawKey)
      throws Exception {
    IdempotencyKey key = IdempotencyKey.of(rawKey);
    mvc.perform(
            post("/internal/v1/wallet/transactions/credit")
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, caller.wireName())
                .header(
                    InternalApiKeyAuthenticationFilter.API_KEY_HEADER,
                    TestInternalApiKeys.key(caller))
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, rawKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\""
                        + USER_ID
                        + "\",\"amount\":{\"amount\":100,\"currency\":\"KRW\"},"
                        + "\"source\":\""
                        + source
                        + "\",\"reason\":\""
                        + reason
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(5)))
        .andExpect(jsonPath("$.caller").doesNotExist());
    verify(wallet).credit(caller, new CreditCommand(USER_ID, Money.krw(100), source, reason, key));
  }
}
