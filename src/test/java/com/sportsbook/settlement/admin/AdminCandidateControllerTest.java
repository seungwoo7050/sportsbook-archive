package com.sportsbook.settlement.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminCandidateControllerTest {

  @Test
  void approvesWithTheTypedIdempotencyKey() throws Exception {
    AdminCandidateCommands commands = mock(AdminCandidateCommands.class);
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    when(commands.approve(key, candidateId))
        .thenReturn(new AdminCandidateCommands.Receipt(key, "CANDIDATE_APPROVED", false));

    mvc(commands)
        .perform(
            post("/internal/admin/result-candidates/{id}/approve", candidateId)
                .header("Idempotency-Key", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idempotencyKey").value(key.toString()))
        .andExpect(jsonPath("$.outcome").value("CANDIDATE_APPROVED"))
        .andExpect(jsonPath("$.replay").value(false));
    verify(commands).approve(key, candidateId);
  }

  @Test
  void rendersSafeRejectConflictsAndInvalidBodies() throws Exception {
    AdminCandidateCommands commands = mock(AdminCandidateCommands.class);
    UUID key = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    when(commands.reject(key, candidateId, "bad result"))
        .thenThrow(AdminControlException.conflict("Result candidate is already decided"));
    MockMvc mvc = mvc(commands);

    mvc.perform(
            post("/internal/admin/result-candidates/{id}/reject", candidateId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"bad result\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Result candidate is already decided"));
    mvc.perform(
            post("/internal/admin/result-candidates/{id}/reject", candidateId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The admin request is invalid"));
  }

  private static MockMvc mvc(AdminCandidateCommands commands) {
    return MockMvcBuilders.standaloneSetup(new AdminCandidateController(commands))
        .setControllerAdvice(new AdminExceptionHandler())
        .build();
  }
}
