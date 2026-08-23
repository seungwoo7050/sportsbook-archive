package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

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
}
