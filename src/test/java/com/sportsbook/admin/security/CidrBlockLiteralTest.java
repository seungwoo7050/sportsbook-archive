package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class CidrBlockLiteralTest {

  @Test
  void parsesCanonicalIpv4AndIpv6Literals() throws Exception {
    assertThat(CidrBlock.parseAddress("127.0.0.1"))
        .contains(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
    assertThat(CidrBlock.parseAddress("2001:db8::1"))
        .contains(InetAddress.getByName("2001:db8::1"));
  }

  @Test
  void rejectsAlternateNumericFormsAndHostnamesWithoutResolution() {
    assertThat(CidrBlock.parseAddress("2130706433")).isEmpty();
    assertThat(CidrBlock.parseAddress("127.1")).isEmpty();
    assertThat(CidrBlock.parseAddress("0177.0.0.1")).isEmpty();
    assertThat(CidrBlock.parseAddress("deadbeef")).isEmpty();
    assertThat(CidrBlock.parseAddress("localhost")).isEmpty();
    assertThat(CidrBlock.parseAddress("example.com")).isEmpty();

    assertThatThrownBy(() -> CidrBlock.parse("2130706433/8"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
