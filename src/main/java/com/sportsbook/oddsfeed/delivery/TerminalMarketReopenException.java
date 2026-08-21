package com.sportsbook.oddsfeed.delivery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when an operator attempts to reopen a terminal event or market. */
@ResponseStatus(HttpStatus.CONFLICT)
public class TerminalMarketReopenException extends RuntimeException {

  public TerminalMarketReopenException() {
    super("A terminal event or market cannot be reopened");
  }
}
