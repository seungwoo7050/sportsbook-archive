package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementIdempotencyRequestTest {

  private static final UUID REVISION = UUID.fromString("018f0000-0000-7000-8000-000000000180");

  @Test
  void rendersMissingDuplicateAndInvalidUuidKeysAsValidationProblems() throws Exception {
    SettlementClient settlements = mock(SettlementClient.class);
    MockMvc mvc = mvc(settlements);

    String actionId =
        mvc.perform(post(path()).principal(authentication()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(header().exists(AdminContextArgumentResolver.ACTION_ID_HEADER))
            .andReturn()
            .getResponse()
            .getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER);
    assertThat(UUID.fromString(actionId).version()).isEqualTo(7);

    mvc.perform(
            post(path())
                .principal(authentication())
                .header(AdminRequestHeaders.IDEMPOTENCY_KEY, "first", "second"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    mvc.perform(
            post(path())
                .principal(authentication())
                .header(AdminRequestHeaders.IDEMPOTENCY_KEY, "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(settlements);
  }

  private static MockMvc mvc(SettlementClient settlements) {
    return MockMvcBuilders.standaloneSetup(new SettlementRevisionCommandController(settlements))
        .setCustomArgumentResolvers(new AdminContextArgumentResolver())
        .setControllerAdvice(new AdminExceptionHandler())
        .build();
  }

  private static String path() {
    return "/admin/v1/settlements/revisions/" + REVISION + "/retry";
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("operator-1")
            .claim("role", "TRADER")
            .build();
    return new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_TRADER")), "operator-1");
  }
}
