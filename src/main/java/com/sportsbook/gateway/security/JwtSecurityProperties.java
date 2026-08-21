package com.sportsbook.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gateway.security.jwt")
public record JwtSecurityProperties(String publicKey) {}
