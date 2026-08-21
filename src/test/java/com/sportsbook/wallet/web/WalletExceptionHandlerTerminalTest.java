package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class WalletExceptionHandlerTerminalTest {
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ProbeController())
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void mapsIllegalArgumentsToFixedInvalidRequests() throws Exception {
    assertProblem(
        mvc.perform(
            get("/probe/illegal?trace=secret-query").header("X-Diagnostic", "secret-header")),
        WalletError.INVALID_REQUEST,
        "Wallet request is malformed or violates validation constraints",
        "/probe/illegal");
  }

  @Test
  void mapsUnexpectedFailuresToSafeInternalErrors() throws Exception {
    assertProblem(
        mvc.perform(
            get("/probe/internal?trace=secret-query").header("X-Diagnostic", "secret-header")),
        WalletError.INTERNAL_ERROR,
        "Wallet request could not be completed",
        "/probe/internal");
  }

  private void assertProblem(
      ResultActions result, WalletError error, String detail, String instance) throws Exception {
    result
        .andExpect(status().is(error.httpStatus()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(6)))
        .andExpect(jsonPath("$.status").value(error.httpStatus()))
        .andExpect(jsonPath("$.type").value(error.type().toString()))
        .andExpect(jsonPath("$.title").value(error.title()))
        .andExpect(jsonPath("$.detail").value(detail))
        .andExpect(jsonPath("$.instance").value(instance))
        .andExpect(jsonPath("$.errorCode").value(error.errorCode()))
        .andExpect(content().string(not(containsString("secret"))));
  }

  @RestController
  static class ProbeController {
    @GetMapping("/probe/illegal")
    void illegal() {
      throw new IllegalArgumentException("secret invalid detail");
    }

    @GetMapping("/probe/internal")
    void internal() {
      throw new IllegalStateException("secret internal detail");
    }
  }
}
