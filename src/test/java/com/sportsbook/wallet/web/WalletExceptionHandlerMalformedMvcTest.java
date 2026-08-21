package com.sportsbook.wallet.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class WalletExceptionHandlerMalformedMvcTest {
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ProbeController())
          .setControllerAdvice(new WalletExceptionHandler())
          .build();

  @Test
  void mapsValidationAndUnreadableJsonToFixedProblems() throws Exception {
    assertInvalid(
        mvc.perform(
            post("/probe/body?trace=secret-query")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Api-Key", "secret-header")
                .content("{\"value\":\"\"}")),
        "/probe/body");
    assertInvalid(
        mvc.perform(
            post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"secret-body\"")),
        "/probe/body");
  }

  @Test
  void mapsTypeHeaderAndMediaFailuresToFixedProblems() throws Exception {
    assertInvalid(mvc.perform(get("/probe/not-a-uuid")), "/probe/not-a-uuid");
    assertInvalid(mvc.perform(get("/probe/header")), "/probe/header");
    assertInvalid(mvc.perform(get("/probe/validated?value=")), "/probe/validated");
    assertInvalid(
        mvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("secret-body")),
        "/probe/body");
  }

  private void assertInvalid(ResultActions result, String instance) throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$", aMapWithSize(6)))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value(WalletError.INVALID_REQUEST.type().toString()))
        .andExpect(jsonPath("$.title").value(WalletError.INVALID_REQUEST.title()))
        .andExpect(
            jsonPath("$.detail")
                .value("Wallet request is malformed or violates validation constraints"))
        .andExpect(jsonPath("$.instance").value(instance))
        .andExpect(jsonPath("$.errorCode").value(WalletError.INVALID_REQUEST.errorCode()))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @RestController
  static class ProbeController {
    @PostMapping(path = "/probe/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    void body(@Valid @RequestBody ProbeBody body) {}

    @GetMapping("/probe/{id}")
    void type(@PathVariable("id") UUID id) {}

    @GetMapping("/probe/header")
    void header(@RequestHeader("Idempotency-Key") String key) {}

    @GetMapping("/probe/validated")
    void validated(@RequestParam @NotBlank String value) {}
  }

  record ProbeBody(@NotBlank String value) {}
}
