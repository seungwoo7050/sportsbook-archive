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
import com.sportsbook.wallet.service.command.DepositCommand;
import com.sportsbook.wallet.service.command.WithdrawCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformTransactionControllerTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000441");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-000000000442");
  private static final IdempotencyKey KEY = IdempotencyKey.of("platform:transaction:441");
  private static final Instant AT = Instant.parse("2026-08-21T17:00:00Z");

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new PlatformTransactionController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void depositsExternalFundsWithTheExactRequestIdentity() throws Exception {
    when(wallet.deposit(new DepositCommand(USER_ID, Money.krw(700), KEY)))
        .thenReturn(result(LedgerReason.DEPOSIT));

    mvc.perform(
            post("/internal/v1/wallet/transactions/deposit")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(5)))
        .andExpect(jsonPath("$.operationGroupId").value(GROUP_ID.toString()))
        .andExpect(jsonPath("$.amount.amount").value(700))
        .andExpect(jsonPath("$.reason").value("DEPOSIT"));

    verify(wallet).deposit(new DepositCommand(USER_ID, Money.krw(700), KEY));
  }

  @Test
  void withdrawsExternalFundsWithTheExactRequestIdentity() throws Exception {
    when(wallet.withdraw(new WithdrawCommand(USER_ID, Money.krw(700), KEY)))
        .thenReturn(result(LedgerReason.WITHDRAW));

    mvc.perform(
            post("/internal/v1/wallet/transactions/withdraw")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reason").value("WITHDRAW"));

    verify(wallet).withdraw(new WithdrawCommand(USER_ID, Money.krw(700), KEY));
  }

  private WalletOperationResult result(LedgerReason reason) {
    return new WalletOperationResult(GROUP_ID, USER_ID, Money.krw(700), reason, AT);
  }

  private String body() {
    return "{\"userId\":\"" + USER_ID + "\",\"amount\":{\"amount\":700,\"currency\":\"KRW\"}}";
  }
}
