package com.sportsbook.settlement.readmodel;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/** Builds the same semantic identity from an incoming or persisted placement snapshot. */
public final class BetPlacementFingerprinter {

  public String fingerprint(BetPlacement placement) {
    return fingerprint(
        placement.betId(),
        placement.userId(),
        placement.slipType(),
        placement.unitStake(),
        placement.requestedAt().toEpochMilli(),
        placement.selections().stream().map(this::canonical).toList());
  }

  public String fingerprint(Bet bet) {
    return fingerprint(
        bet.betId(),
        bet.userId(),
        bet.slipType(),
        bet.stake(),
        bet.requestedAt().toEpochMilli(),
        bet.selections().stream().map(this::canonical).toList());
  }

  private String fingerprint(
      UUID betId,
      UUID userId,
      BetSlipType slipType,
      Money stake,
      long requestedAt,
      List<String> selections) {
    StringJoiner fields = new StringJoiner("\0");
    fields.add(betId.toString()).add(userId.toString()).add(kind(slipType));
    if (slipType instanceof BetSlipType.System system) {
      fields.add(Integer.toString(system.minWins()));
      fields.add(Integer.toString(system.totalSelections()));
    } else {
      fields.add("").add("");
    }
    fields.add(Long.toString(stake.amount())).add(stake.currency().name());
    fields.add(Long.toString(requestedAt)).add(Integer.toString(selections.size()));
    selections.forEach(fields::add);
    return sha256(fields.toString());
  }

  private String canonical(BetPlacement.Selection selection) {
    return canonical(
        selection.eventId(), selection.marketId(), selection.selectionId(), selection.odds());
  }

  private String canonical(BetSelection selection) {
    return canonical(
        selection.eventId(), selection.marketId(), selection.selectionId(), selection.odds());
  }

  private String canonical(UUID eventId, UUID marketId, UUID selectionId, Odds odds) {
    return String.join(
        ":",
        eventId.toString(),
        marketId.toString(),
        selectionId.toString(),
        Odds.ofDecimal(odds.decimal()).decimal().toPlainString());
  }

  private static String kind(BetSlipType type) {
    if (type instanceof BetSlipType.Single) {
      return "SINGLE";
    }
    if (type instanceof BetSlipType.Multiple) {
      return "MULTIPLE";
    }
    return "SYSTEM";
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK must provide SHA-256", exception);
    }
  }
}
