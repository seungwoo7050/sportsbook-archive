package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdjustmentControllerLookupTest {
  private static final UUID APPLIED_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c1");
  private static final UUID BLOCKED_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c2");
  private static final UUID REJECTED_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c3");
  private static final UUID MISSING_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c4");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c5");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c6");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004c7");
  private static final Instant NOW = Instant.parse("2026-08-21T21:00:00Z");

  private final WalletAdjustmentService adjustments = mock(WalletAdjustmentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AdjustmentController(adjustments))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void returnsEveryDurableProofStatus() throws Exception {
    AdjustmentCommand applied = command(APPLIED_ID, 700L, 1_000L);
    AdjustmentCommand blocked = command(BLOCKED_ID, 1_000L, 700L);
    AdjustmentCommand rejected = command(REJECTED_ID, 1_000L, 700L);
    when(adjustments.findProof(APPLIED_ID))
        .thenReturn(Optional.of(WalletAdjustment.applied(applied, GROUP_ID, NOW)));
    when(adjustments.findProof(BLOCKED_ID))
        .thenReturn(Optional.of(WalletAdjustment.blocked(blocked, 4L, NOW)));
    when(adjustments.findProof(REJECTED_ID))
        .thenReturn(Optional.of(WalletAdjustment.rejected(rejected, NOW)));

    assertStatus(APPLIED_ID, "APPLIED");
    assertStatus(BLOCKED_ID, "BLOCKED");
    assertStatus(REJECTED_ID, "REJECTED");
  }

  @Test
  void mapsMissingProofsWithoutReflectingTheirIdentity() throws Exception {
    when(adjustments.findProof(MISSING_ID)).thenReturn(Optional.empty());

    MvcResult result =
        mvc.perform(get(path(MISSING_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("WALLET_ADJUSTMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("The requested wallet adjustment does not exist"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .contains("\"instance\":\"" + path(MISSING_ID) + "\"")
        .doesNotContain("No wallet adjustment exists");
    verify(adjustments).findProof(MISSING_ID);
  }

  private void assertStatus(UUID revisionId, String expected) throws Exception {
    mvc.perform(get(path(revisionId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionId").value(revisionId.toString()))
        .andExpect(jsonPath("$.status").value(expected));
  }

  private AdjustmentCommand command(UUID revisionId, long previous, long next) {
    return new AdjustmentCommand(
        revisionId,
        BET_ID,
        2L,
        USER_ID,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private String path(UUID revisionId) {
    return "/internal/v1/wallet/transactions/adjustment/" + revisionId;
  }
}
