package com.sportsbook.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RestExceptionHandlerTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void masksUnexpectedFailuresWithTheSharedProblemShape() throws Exception {
    String body =
        mvc.perform(get("/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("private-detail");
  }

  @RestController
  private static class FailingController {
    @GetMapping("/failure")
    void fail() {
      throw new IllegalArgumentException("private-detail");
    }
  }
}
