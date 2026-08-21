package com.sportsbook.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {"spring.main.web-application-type=none", "management.tracing.enabled=false"})
class GatewayApplicationTest {

  @Test
  void loadsApplicationContext() {}

  @Test
  void exposesPublicMainEntryPoint() throws NoSuchMethodException {
    Method main = GatewayApplication.class.getMethod("main", String[].class);

    assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
    assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
  }
}
