package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaPublicKeyParserTest {

  private final RsaPublicKeyParser parser = new RsaPublicKeyParser();

  @Test
  void acceptsA2048BitSpkiKeyWithRealOrEscapedNewlines() throws Exception {
    RSAPublicKey key = (RSAPublicKey) rsaKeyPair(2048).getPublic();
    String pem = pem(key);

    assertThat(parser.parse(pem)).isEqualTo(key);
    assertThat(parser.parse(pem.replace("\n", "\\n"))).isEqualTo(key);
  }

  @Test
  void rejectsMissingMalformedAndWrongPemMarkersWithoutEchoingTheKey() {
    String secret = "-----BEGIN RSA PUBLIC KEY-----\nsensitive\n-----END RSA PUBLIC KEY-----";

    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> parser.parse("not-a-key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("not-a-key");
    assertThatThrownBy(() -> parser.parse(secret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(secret);
  }

  @Test
  void rejectsRsaKeysSmallerThan2048Bits() throws Exception {
    String weakKey = pem(rsaKeyPair(1024).getPublic());

    assertThatThrownBy(() -> parser.parse(weakKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 2048 bits")
        .hasMessageNotContaining(weakKey);
  }

  private static KeyPair rsaKeyPair(int bits) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    return generator.generateKeyPair();
  }

  private static String pem(PublicKey key) {
    String body = Base64.getEncoder().encodeToString(key.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
  }
}
