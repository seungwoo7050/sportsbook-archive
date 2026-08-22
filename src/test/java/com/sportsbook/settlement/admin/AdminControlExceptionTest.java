package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminControlExceptionTest {

  @Test
  void carriesOnlyTheExplicitSafeStatusAndDetail() {
    AdminControlException invalid = AdminControlException.invalid("Invalid command");
    AdminControlException missing = AdminControlException.notFound("Revision");
    AdminControlException conflict = AdminControlException.conflict("Command already decided");

    assertThat(invalid.status().value()).isEqualTo(400);
    assertThat(invalid).hasMessage("Invalid command").hasNoCause();
    assertThat(missing.status().value()).isEqualTo(404);
    assertThat(missing).hasMessage("Revision was not found").hasNoCause();
    assertThat(conflict.status().value()).isEqualTo(409);
    assertThat(conflict).hasMessage("Command already decided").hasNoCause();
  }
}
