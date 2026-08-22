package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdminRoleTest {

  @Test
  void mapsOnlyExactUppercaseRoleClaims() {
    assertThat(AdminRole.fromClaim("ADMIN")).contains(AdminRole.ADMIN);
    assertThat(AdminRole.fromClaim("TRADER")).contains(AdminRole.TRADER);
    assertThat(AdminRole.fromClaim("CS")).contains(AdminRole.CS);
    assertThat(AdminRole.fromClaim("READONLY")).contains(AdminRole.READONLY);
  }

  @Test
  void rejectsMissingMalformedAndUnknownRoleClaims() {
    assertThat(AdminRole.fromClaim(null)).isEmpty();
    assertThat(AdminRole.fromClaim("")).isEmpty();
    assertThat(AdminRole.fromClaim("admin")).isEmpty();
    assertThat(AdminRole.fromClaim(" ADMIN ")).isEmpty();
    assertThat(AdminRole.fromClaim("OWNER")).isEmpty();
    assertThat(AdminRole.fromClaim(List.of("ADMIN"))).isEmpty();
  }

  @Test
  void exposesSpringRoleAuthorities() {
    assertThat(AdminRole.TRADER.authority()).isEqualTo("ROLE_TRADER");
  }
}
