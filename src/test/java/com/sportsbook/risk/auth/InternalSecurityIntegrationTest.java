package com.sportsbook.risk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "risk.auth.betting-service-api-key=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "risk.auth.admin-api-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "risk.auth.platform-api-key=pppppppppppppppppppppppppppppppp",
      "management.health.redis.enabled=false"
    })
@AutoConfigureMockMvc
class InternalSecurityIntegrationTest {
  private static final String BETTING = "b".repeat(32);
  private static final String ADMIN = "a".repeat(32);
  private static final String PLATFORM = "p".repeat(32);

  @Autowired private MockMvc mvc;

  @Test
  void permitsOnlyTheOwnerOfEachInternalRoute() throws Exception {
    List<Route> routes =
        List.of(
            new Route(
                HttpMethod.POST, "/internal/v1/risk/reservations", "betting-service", BETTING),
            new Route(
                HttpMethod.PUT,
                "/internal/v1/risk/reservations/00000000-0000-0000-0000-000000000001/commit",
                "betting-service",
                BETTING),
            new Route(
                HttpMethod.DELETE,
                "/internal/v1/risk/reservations/00000000-0000-0000-0000-000000000001",
                "betting-service",
                BETTING),
            new Route(HttpMethod.GET, "/internal/v1/risk/limits/x", "admin-api", ADMIN),
            new Route(HttpMethod.PATCH, "/internal/v1/risk/limits/x", "admin-api", ADMIN),
            new Route(HttpMethod.POST, "/internal/v1/risk/check", "platform", PLATFORM));

    for (Route route : routes) {
      int ownerStatus = mvc.perform(route.request()).andReturn().getResponse().getStatus();
      assertThat(ownerStatus).isNotIn(401, 403);
      String otherCaller = route.caller.equals("platform") ? "admin-api" : "platform";
      String otherSecret = route.caller.equals("platform") ? ADMIN : PLATFORM;
      assertThat(
              mvc.perform(route.request(otherCaller, otherSecret))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .isEqualTo(403);
    }
  }

  @Test
  void distinguishesAnonymousHealthFromProtectedRequests() throws Exception {
    assertThat(mvc.perform(get("/actuator/health/liveness")).andReturn().getResponse().getStatus())
        .isEqualTo(200);
    assertThat(mvc.perform(post("/internal/v1/risk/check")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
  }

  private record Route(HttpMethod method, String path, String caller, String secret) {
    MockHttpServletRequestBuilder request() {
      return request(caller, secret);
    }

    MockHttpServletRequestBuilder request(String service, String key) {
      MockHttpServletRequestBuilder request =
          switch (method.name()) {
            case "GET" -> get(path);
            case "PATCH" -> patch(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("unsupported method");
          };
      return request
          .header(InternalAuthenticationFilter.SERVICE_HEADER, service)
          .header(InternalAuthenticationFilter.API_KEY_HEADER, key);
    }
  }
}
