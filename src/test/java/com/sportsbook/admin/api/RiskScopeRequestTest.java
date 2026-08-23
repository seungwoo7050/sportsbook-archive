package com.sportsbook.admin.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.client.RiskClient;
import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RiskScopeRequestTest {

  private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000181");

  @Test
  void rendersInvalidDeleteScopesBeforeCallingRisk() throws Exception {
    RiskClient risk = mock(RiskClient.class);
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new RiskAdminController(risk))
            .setCustomArgumentResolvers(new AdminContextArgumentResolver())
            .setControllerAdvice(new AdminExceptionHandler())
            .build();

    mvc.perform(delete(path("STAKE_DAILY")).principal(authentication()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(header().exists(AdminContextArgumentResolver.ACTION_ID_HEADER));
    mvc.perform(
            delete(path("SELECTIONS_PER_MINUTE"))
                .queryParam("currency", "KRW")
                .principal(authentication()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(header().exists(AdminContextArgumentResolver.ACTION_ID_HEADER));
    verifyNoInteractions(risk);
  }

  private static String path(String type) {
    return "/admin/v1/risk/users/" + USER + "/limits/" + type;
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
