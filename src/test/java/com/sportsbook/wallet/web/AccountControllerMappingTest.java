package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerMappingTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000411");
  private static final Instant NOW = Instant.parse("2026-08-21T14:00:00Z");

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AccountController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void opensAccountsWithTheValidatedRequest() throws Exception {
    Account account = Account.openFor(USER_ID, Currency.KRW, NOW);
    when(wallet.openAccount(new OpenAccountCommand(USER_ID, Currency.KRW))).thenReturn(account);

    mvc.perform(
            post("/internal/v1/wallet/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\",\"currency\":\"KRW\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(8)))
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.currency").value("KRW"))
        .andExpect(jsonPath("$.available.amount").value(0))
        .andExpect(jsonPath("$.outboundFrozen").value(false));

    verify(wallet).openAccount(new OpenAccountCommand(USER_ID, Currency.KRW));
  }

  @Test
  void returnsFrozenBalanceSnapshots() throws Exception {
    Account account = Account.openFor(USER_ID, Currency.USD, NOW);
    account.increaseAvailable(Money.usd(900), NOW.plusSeconds(1));
    account.moveAvailableToLocked(Money.usd(200), NOW.plusSeconds(2));
    account.queueRecoveryDebt(Money.usd(100), NOW.plusSeconds(3));
    when(wallet.requireAccount(USER_ID)).thenReturn(account);

    mvc.perform(get("/internal/v1/wallet/accounts/{userId}/balance", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(5)))
        .andExpect(jsonPath("$.available.amount").value(700))
        .andExpect(jsonPath("$.locked.amount").value(200))
        .andExpect(jsonPath("$.total.amount").value(900))
        .andExpect(jsonPath("$.outboundFrozen").value(true));

    verify(wallet).requireAccount(USER_ID);
  }

  @Test
  void rejectsMissingAccountFieldsBeforeCallingTheService() throws Exception {
    mvc.perform(
            post("/internal/v1/wallet/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));

    verifyNoInteractions(wallet);
  }
}
