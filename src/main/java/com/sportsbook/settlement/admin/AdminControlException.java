package com.sportsbook.settlement.admin;

import org.springframework.http.HttpStatus;

public final class AdminControlException extends RuntimeException {

  private final HttpStatus status;

  private AdminControlException(HttpStatus status, String safeDetail) {
    super(safeDetail);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static AdminControlException invalid(String safeDetail) {
    return new AdminControlException(HttpStatus.BAD_REQUEST, safeDetail);
  }

  public static AdminControlException notFound(String resource) {
    return new AdminControlException(HttpStatus.NOT_FOUND, resource + " was not found");
  }

  public static AdminControlException conflict(String safeDetail) {
    return new AdminControlException(HttpStatus.CONFLICT, safeDetail);
  }
}
