package com.sportsbook.risk.reservation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sportsbook.risk.pattern.PatternMatch;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Strictly validates deterministic admission and replay results from Redis. */
@Component
public final class ReservationWireMapper {
  private final ObjectReader reader;
  private final ObjectMapper mapper;

  public ReservationWireMapper(ObjectMapper mapper) {
    Objects.requireNonNull(mapper, "mapper");
    this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    reader = this.mapper.readerFor(ReservationWire.class);
  }

  public Decoded map(String raw) {
    ReservationWire wire = read(raw);
    if (!"1".equals(wire.version()) || wire.status() == null || wire.replayed() == null) {
      throw ReservationWireValidator.malformed();
    }
    long expired = ReservationWireValidator.exact(wire.expired(), "expired");
    List<PatternMatch> patterns = patterns(wire.patternsJson());
    ReservationDecision decision = ReservationWireValidator.decision(wire, patterns);
    return new Decoded(decision, expired);
  }

  private ReservationWire read(String raw) {
    if (raw == null) {
      throw ReservationWireValidator.malformed();
    }
    try {
      return reader.readValue(raw);
    } catch (Exception failure) {
      throw ReservationWireValidator.malformed(failure);
    }
  }

  private List<PatternMatch> patterns(String raw) {
    if (raw == null) {
      return List.of();
    }
    try {
      return List.copyOf(mapper.readValue(raw, new TypeReference<List<PatternMatch>>() {}));
    } catch (Exception failure) {
      throw ReservationWireValidator.malformed(failure);
    }
  }

  public record Decoded(ReservationDecision decision, long expired) {}
}
