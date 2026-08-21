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
import com.sportsbook.wallet.service.command.ForfeitCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ForfeitControllerTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000481");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000482");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:forfeit:481");
  private static final Instant AT = Instant.parse("2026-08-21T18:00:00Z");

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ForfeitController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void forfeitsLockedFundsWithTheExactRequestIdentity() throws Exception {
    var command = new ForfeitCommand(USER_ID, Money.krw(500), KEY);
    when(wallet.forfeit(command))
        .thenReturn(
            new WalletOperationResult(
                GROUP_ID, USER_ID, Money.krw(500), LedgerReason.BET_FORFEIT, AT));

    mvc.perform(
            post("/internal/v1/wallet/transactions/forfeit")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\""
                        + USER_ID
                        + "\",\"amount\":{\"amount\":500,\"currency\":\"KRW\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(5)))
        .andExpect(jsonPath("$.operationGroupId").value(GROUP_ID.toString()))
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.amount.amount").value(500))
        .andExpect(jsonPath("$.reason").value("BET_FORFEIT"));

    verify(wallet).forfeit(command);
  }
}
