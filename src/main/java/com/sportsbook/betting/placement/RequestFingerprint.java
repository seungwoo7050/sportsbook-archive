package com.sportsbook.betting.placement;

import com.sportsbook.protocol.domain.BetSlipType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprint {

  public static String of(PlaceBetCommand command) {
    StringBuilder canonical =
        new StringBuilder()
            .append(command.userId())
            .append('|')
            .append(slipType(command.slipType()))
            .append('|')
            .append(command.unitStake().amount())
            .append('|')
            .append(command.unitStake().currency());
    for (PlaceBetCommand.SelectionInput selection : command.selections()) {
      canonical
          .append('|')
          .append(selection.eventId())
          .append('|')
          .append(selection.marketId())
          .append('|')
          .append(selection.selectionId())
          .append('|')
          .append(selection.oddsAtSubmission().decimal().stripTrailingZeros().toPlainString());
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static String slipType(BetSlipType type) {
    if (type instanceof BetSlipType.System system) {
      return "SYSTEM:" + system.minWins() + ':' + system.totalSelections();
    }
    return type instanceof BetSlipType.Single ? "SINGLE" : "MULTIPLE";
  }

  private RequestFingerprint() {}
}
