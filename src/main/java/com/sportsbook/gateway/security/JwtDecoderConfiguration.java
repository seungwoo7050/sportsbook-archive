package com.sportsbook.gateway.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class JwtDecoderConfiguration {

  @Bean
  JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
    RSAPublicKey key = new RsaPublicKeyLoader().load(properties.publicKey());
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();

    OAuth2TokenValidator<Jwt> timestamps = new JwtTimestampValidator(Duration.ZERO);
    OAuth2TokenValidator<Jwt> requiredExpiry =
        new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, requiredExpiry));
    return decoder;
  }
}
