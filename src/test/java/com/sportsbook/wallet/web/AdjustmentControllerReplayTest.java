package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdjustmentControllerReplayTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004f1");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004f2");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004f3");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:revision:" + REVISION_ID);

  private final WalletAdjustmentService adjustments = mock(WalletAdjustmentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AdjustmentController(adjustments))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void replaysStoredAdjustmentRejectionsWithoutSynthesizingAProof() throws Exception {
    AdjustmentCommand command =
        new AdjustmentCommand(
            REVISION_ID, BET_ID, 2L, USER_ID, Money.krw(700), Money.krw(1_000), KEY);
    WalletFailureSnapshot stored =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.AMOUNT_OUT_OF_RANGE,
            "stored aggregate range at rejection",
            Money.krw(Long.MAX_VALUE));
    when(adjustments.adjust(command)).thenThrow(new WalletRejectedException(stored));

    mvc.perform(
            post("/internal/v1/wallet/transactions/adjustment")
                .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$", aMapWithSize(7)))
        .andExpect(jsonPath("$.title").value("Amount out of range"))
        .andExpect(jsonPath("$.detail").value("stored aggregate range at rejection"))
        .andExpect(jsonPath("$.errorCode").value("WALLET_AMOUNT_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.balance.amount").value(Long.MAX_VALUE))
        .andExpect(jsonPath("$.balance.currency").value("KRW"));
    verify(adjustments).adjust(command);
  }

  private String body() {
    return "{\"revisionId\":\""
        + REVISION_ID
        + "\",\"betId\":\""
        + BET_ID
        + "\",\"revisionNumber\":2,\"userId\":\""
        + USER_ID
        + "\",\"previousPayout\":{\"amount\":700,\"currency\":\"KRW\"},"
        + "\"newPayout\":{\"amount\":1000,\"currency\":\"KRW\"}}";
  }
}
