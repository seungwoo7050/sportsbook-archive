package com.sportsbook.settlement.result;

import com.sportsbook.protocol.domain.SettlementResult;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "match_result")
public class MatchResultRecord {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mode", nullable = false, length = 16)
  private MatchOutcomeMode mode;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "match_selection_result", joinColumns = @JoinColumn(name = "event_id"))
  @MapKeyColumn(name = "selection_id")
  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 8)
  private Map<UUID, SettlementResult> outcomes = new HashMap<>();

  @Column(name = "settled_at", nullable = false)
  private Instant settledAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected MatchResultRecord() {}

  public MatchResultRecord(
      UUID eventId,
      MatchOutcomeMode mode,
      Map<UUID, SettlementResult> outcomes,
      Instant settledAt,
      Instant receivedAt) {
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.mode = Objects.requireNonNull(mode, "mode");
    this.outcomes = new HashMap<>(Objects.requireNonNull(outcomes, "outcomes"));
    this.settledAt = Objects.requireNonNull(settledAt, "settledAt");
    this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
  }

  public UUID eventId() {
    return eventId;
  }

  public MatchOutcomeMode mode() {
    return mode;
  }

  public Map<UUID, SettlementResult> outcomes() {
    return Map.copyOf(outcomes);
  }

  public Instant settledAt() {
    return settledAt;
  }

  public Instant receivedAt() {
    return receivedAt;
  }
}
