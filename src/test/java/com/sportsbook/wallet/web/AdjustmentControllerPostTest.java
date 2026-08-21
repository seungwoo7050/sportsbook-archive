package com.sportsbook.wallet.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdjustmentControllerPostTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004b1");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004b2");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004b3");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004b4");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:revision:" + REVISION_ID);
  private static final Instant NOW = Instant.parse("2026-08-21T20:00:00Z");

  private final WalletAdjustmentService adjustments = mock(WalletAdjustmentService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new AdjustmentController(adjustments))
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void appliesPositiveRevisionsWithNoProofLocation() throws Exception {
    AdjustmentCommand command = command(700L, 1_000L);
    when(adjustments.adjust(command)).thenReturn(WalletAdjustment.applied(command, GROUP_ID, NOW));

    mvc.perform(request(700L, 1_000L))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.status").value("APPLIED"))
        .andExpect(jsonPath("$.operationGroupId").value(GROUP_ID.toString()))
        .andExpect(jsonPath("$.deltaAmount").value(300));
    verify(adjustments).adjust(command);
  }

  @Test
  void queuesNegativeRevisionsAtTheirDurableProofLocation() throws Exception {
    AdjustmentCommand command = command(1_000L, 700L);
    when(adjustments.adjust(command)).thenReturn(WalletAdjustment.blocked(command, 4L, NOW));

    mvc.perform(request(1_000L, 700L))
        .andExpect(status().isAccepted())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    "/internal/v1/wallet/transactions/adjustment/" + REVISION_ID))
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.queueSequence").value(4))
        .andExpect(jsonPath("$.operationGroupId").isEmpty())
        .andExpect(jsonPath("$.deltaAmount").value(-300));
    verify(adjustments).adjust(command);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      long previous, long next) {
    return post("/internal/v1/wallet/transactions/adjustment")
        .header(WalletRequestHeaders.IDEMPOTENCY_KEY, KEY.value())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body(previous, next));
  }

  private AdjustmentCommand command(long previous, long next) {
    return new AdjustmentCommand(
        REVISION_ID, BET_ID, 2L, USER_ID, Money.krw(previous), Money.krw(next), KEY);
  }

  private String body(long previous, long next) {
    return "{\"revisionId\":\""
        + REVISION_ID
        + "\",\"betId\":\""
        + BET_ID
        + "\",\"revisionNumber\":2,\"userId\":\""
        + USER_ID
        + "\",\"previousPayout\":{\"amount\":"
        + previous
        + ",\"currency\":\"KRW\"},\"newPayout\":{\"amount\":"
        + next
        + ",\"currency\":\"KRW\"}}";
  }
}
