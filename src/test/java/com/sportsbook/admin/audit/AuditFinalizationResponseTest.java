package com.sportsbook.admin.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.api.AdminExceptionHandler;
import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class AuditFinalizationResponseTest {

  private static final UUID ACTION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000051");

  @Test
  void returns503WithTheSameActionIdWhenFinalizationFails() throws Exception {
    AuditPersistenceException failure =
        new AuditPersistenceException(
            ACTION_ID,
            AuditPersistenceException.Phase.COMPLETE,
            new IllegalStateException("database unavailable"));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new FailureController(failure))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();

    mvc.perform(post("/admin/v1/test/finalize"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            header().string(AdminContextArgumentResolver.ACTION_ID_HEADER, ACTION_ID.toString()))
        .andExpect(jsonPath("$.errorCode").value("AUDIT_FINALIZATION_FAILED"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.detail").value("The audit trail could not be finalized"));
  }

  @RestController
  private static final class FailureController {

    private final RuntimeException failure;

    private FailureController(RuntimeException failure) {
      this.failure = failure;
    }

    @PostMapping("/admin/v1/test/finalize")
    void fail() {
      throw failure;
    }
  }
}
