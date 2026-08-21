package com.sportsbook.risk.snapshot;

import java.util.List;
import java.util.Map;

/** Precision-safe JSON contract returned by the combined Redis snapshot script. */
record RiskSnapshotWire(
    String version, String expired, Map<String, LimitSlot> limits, PatternFacts patterns) {
  record LimitSlot(Boolean ok, String committed, String active, String override, String error) {}

  record FactSlot(Boolean ok, String value, List<String> values, String error) {}

  record PatternFacts(FactSlot rapid, FactSlot stakes, List<SelectionFact> selections) {}

  record SelectionFact(String selectionId, FactSlot slot) {}
}
