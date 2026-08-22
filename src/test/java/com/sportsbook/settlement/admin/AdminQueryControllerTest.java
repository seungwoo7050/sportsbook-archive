package com.sportsbook.settlement.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminQueryControllerTest {

  private static final String KEY = "0123456789abcdef0123456789abcdef";

  @Test
  void servesSafeCandidateViewsAndProblems() throws Exception {
    AdminCandidateQueryRepository candidates = mock(AdminCandidateQueryRepository.class);
    AdminRevisionQueryRepository revisions = mock(AdminRevisionQueryRepository.class);
    UUID candidateId = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    when(candidates.find(candidateId)).thenReturn(Optional.of(candidate(candidateId)));
    when(revisions.find(revisionId)).thenReturn(Optional.empty());
    MockMvc mvc = mvc(new AdminQueryController(candidates, revisions));

    mvc.perform(
            get("/internal/admin/result-candidates/{id}", candidateId)
                .header(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER)
                .header(AdminCredentials.API_KEY_HEADER, KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidateId").value(candidateId.toString()))
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.leaseToken").doesNotExist());
    mvc.perform(
            get("/internal/admin/revisions/{id}", revisionId)
                .header(AdminCredentials.SERVICE_HEADER, AdminCredentials.CALLER)
                .header(AdminCredentials.API_KEY_HEADER, KEY))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Settlement revision was not found"));
  }

  private static MockMvc mvc(AdminQueryController controller) {
    ObjectMapper mapper = new ObjectMapper();
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new AdminExceptionHandler())
        .addFilters(
            new AdminAuthenticationFilter(
                new AdminCredentials(KEY), new AdminProblemWriter(mapper)))
        .build();
  }

  private static AdminCandidateQueryRepository.View candidate(UUID id) {
    return new AdminCandidateQueryRepository.View(
        id,
        UUID.randomUUID(),
        "COMPLETED",
        Instant.EPOCH,
        Instant.EPOCH,
        "PENDING",
        null,
        "FUTURE_HELD",
        null,
        false);
  }
}
