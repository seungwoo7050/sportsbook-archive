package com.sportsbook.settlement.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminRevisionControllerTest {

  @Test
  void acceptsOnlyTypedQueuedRetryCommands() throws Exception {
    AdminRevisionCommands commands = mock(AdminRevisionCommands.class);
    UUID key = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    Instant due = Instant.parse("2026-08-22T00:00:00Z");
    when(commands.retry(key, revisionId))
        .thenReturn(new AdminRevisionCommands.Receipt(key, "QUEUED", "PENDING", 0, due));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new AdminRevisionController(commands))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();

    mvc.perform(
            post("/internal/admin/revisions/{id}/retry", revisionId).header("Idempotency-Key", key))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.outcome").value("QUEUED"))
        .andExpect(jsonPath("$.revisionState").value("PENDING"))
        .andExpect(jsonPath("$.attemptCount").value(0))
        .andExpect(jsonPath("$.nextRetryAt").exists());
    verify(commands).retry(key, revisionId);

    mvc.perform(
            post("/internal/admin/revisions/{id}/retry", revisionId)
                .header("Idempotency-Key", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The admin request is invalid"));
  }
}
