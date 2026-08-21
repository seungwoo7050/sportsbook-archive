package com.sportsbook.risk.reservation;

/** Precision-safe JSON result emitted by the reservation admission script. */
record ReservationWire(
    String version,
    String expired,
    String status,
    String state,
    String expiresAt,
    String token,
    Boolean replayed,
    String rejection,
    String patternsJson) {}
