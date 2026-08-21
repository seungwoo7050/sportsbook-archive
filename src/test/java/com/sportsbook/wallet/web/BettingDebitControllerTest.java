package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.service.WalletOperationResult;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.DebitCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BettingDebitControllerTest {
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000451");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000452");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000453");
  private static final IdempotencyKey KEY = IdempotencyKey.of(BET_ID.toString());

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new BettingTransactionController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void debitsWithTheCanonicalBetIdentity() throws Exception {
    DebitCommand command = new DebitCommand(USER_ID, Money.krw(300), KEY);
    when(wallet.debit(command))
        .thenReturn(
            new WalletOperationResult(
                GROUP_ID,
                USER_ID,
                Money.krw(300),
                LedgerReason.BET_DEBIT,
                Instant.parse("2026-08-21T18:00:00Z")));

    mvc.perform(
            post("/internal/v1/wallet/transactions/debit")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\""
                        + USER_ID
                        + "\",\"amount\":{\"amount\":300,\"currency\":\"KRW\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(5)))
        .andExpect(jsonPath("$.operationGroupId").value(GROUP_ID.toString()))
        .andExpect(jsonPath("$.reason").value("BET_DEBIT"));

    verify(wallet).debit(command);
  }
}
