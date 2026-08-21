package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.service.WalletOperationResult;
import com.sportsbook.wallet.service.WalletService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BettingDebitLookupTest {
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000456");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000457");

  private final WalletService wallet = mock(WalletService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new BettingTransactionController(wallet))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void returnsTheDurableDebitOutcome() throws Exception {
    when(wallet.findDebit(BET_ID))
        .thenReturn(
            Optional.of(
                new WalletOperationResult(
                    UUID.fromString("019b76da-a000-7000-8000-000000000458"),
                    USER_ID,
                    Money.krw(250),
                    LedgerReason.BET_DEBIT,
                    Instant.parse("2026-08-21T19:00:00Z"))));

    mvc.perform(get("/internal/v1/wallet/transactions/debit/{betId}", BET_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.reason").value("BET_DEBIT"));
  }

  @Test
  void reportsMissingDebitOutcomesWithoutReflectingTheBetIdentity() throws Exception {
    when(wallet.findDebit(BET_ID)).thenReturn(Optional.empty());

    mvc.perform(get("/internal/v1/wallet/transactions/debit/{betId}", BET_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("WALLET_OPERATION_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value(not(containsString(BET_ID.toString()))))
        .andExpect(
            jsonPath("$.instance").value("/internal/v1/wallet/transactions/debit/" + BET_ID));
  }

  @Test
  void replaysPersistedDebitRejections() throws Exception {
    WalletFailureSnapshot stored =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.INSUFFICIENT_BALANCE, "stored debit rejection", Money.krw(10));
    when(wallet.findDebit(BET_ID)).thenThrow(new WalletRejectedException(stored));

    mvc.perform(get("/internal/v1/wallet/transactions/debit/{betId}", BET_ID))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("stored debit rejection"))
        .andExpect(jsonPath("$.errorCode").value("WALLET_INSUFFICIENT_BALANCE"));
  }

  @Test
  void rejectsNoncanonicalDebitPathsBeforeLookup() throws Exception {
    for (String invalid :
        List.of(BET_ID.toString().toUpperCase(Locale.ROOT), "1-1-1-1-1", "not-a-bet-id")) {
      mvc.perform(get("/internal/v1/wallet/transactions/debit/{betId}", invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("WALLET_INVALID_REQUEST"));
    }
    verifyNoInteractions(wallet);
  }
}
