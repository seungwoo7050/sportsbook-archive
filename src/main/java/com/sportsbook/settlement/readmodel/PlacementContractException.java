package com.sportsbook.settlement.readmodel;

/** Permanent placement contract failure that must not be retried. */
public final class PlacementContractException extends IllegalArgumentException {

  public PlacementContractException(String message) {
    super(message);
  }
}
