package com.sportsbook.admin.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
class JwtDecoderConfiguration {

  @Bean
  JwtDecoder adminJwtDecoder(AdminJwtProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(new RsaPublicKeyParser().parse(properties.publicKey()))
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();

    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(new AdminJwtTimestampValidator());
    validators.add(new AdminJwtSubjectValidator());
    validators.add(new AdminJwtRoleValidator());
    properties.expectedIssuer().ifPresent(issuer -> validators.add(new JwtIssuerValidator(issuer)));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }
}
