package com.sportsbook.settlement.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminExceptionHandlerTest {

  @Test
  void hidesUnexpectedExceptionAndDatabaseDetails() throws Exception {
    AdminRevisionCommands commands = mock(AdminRevisionCommands.class);
    UUID key = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    when(commands.retry(key, revisionId))
        .thenThrow(new IllegalStateException("select api_key from operator_secret"));
    var mvc =
        MockMvcBuilders.standaloneSetup(new AdminRevisionController(commands))
            .setControllerAdvice(new AdminExceptionHandler())
            .build();

    mvc.perform(
            post("/internal/admin/revisions/{id}/retry", revisionId).header("Idempotency-Key", key))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("The admin request failed"))
        .andExpect(content().string(not(containsString("select"))))
        .andExpect(content().string(not(containsString("api_key"))));
  }
}
