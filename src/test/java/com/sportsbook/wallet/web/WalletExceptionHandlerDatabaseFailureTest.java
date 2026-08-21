package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class WalletExceptionHandlerDatabaseFailureTest {
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ProbeController())
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void mapsRetryableDatabaseOutagesToBusyResponses() throws Exception {
    for (String path : List.of("/probe/pool", "/probe/connection")) {
      assertProblem(
              mvc.perform(get(path + "?trace=secret-query").header("X-Diagnostic", "secret")),
              WalletError.WALLET_BUSY,
              "Retry the wallet request after one second",
              path)
          .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
    }
  }

  @Test
  void keepsPermanentDatabaseDefectsInternal() throws Exception {
    for (String path : List.of("/probe/constraint", "/probe/syntax")) {
      assertProblem(
              mvc.perform(get(path + "?trace=secret-query").header("X-Diagnostic", "secret")),
              WalletError.INTERNAL_ERROR,
              "Wallet request could not be completed",
              path)
          .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }
  }

  private ResultActions assertProblem(
      ResultActions result, WalletError error, String detail, String instance) throws Exception {
    return result
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
    @GetMapping("/probe/pool")
    void pool() {
      throw new CannotCreateTransactionException(
          "secret transaction detail", new SQLTransientConnectionException("secret pool detail"));
    }

    @GetMapping("/probe/connection")
    void connection() {
      throw new DataAccessResourceFailureException(
          "secret connection detail", new SQLException("secret SQL detail", "08006"));
    }

    @GetMapping("/probe/constraint")
    void constraint() {
      throw new DataIntegrityViolationException(
          "secret duplicate detail", new SQLException("secret constraint detail", "23505"));
    }

    @GetMapping("/probe/syntax")
    void syntax() {
      throw new DataIntegrityViolationException(
          "secret syntax detail", new SQLException("secret SQL detail", "42601"));
    }
  }
}
