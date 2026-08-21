package com.sportsbook.gateway.security;

public final class GatewayHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String INTERNAL_SERVICE = "X-Internal-Service";
  public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

  private GatewayHeaders() {}
}
