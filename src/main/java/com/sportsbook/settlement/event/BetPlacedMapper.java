package com.sportsbook.settlement.event;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.readmodel.BetPlacement;
import com.sportsbook.settlement.readmodel.PlacementContractException;
import java.math.BigDecimal;
import java.util.UUID;

/** Maps the fixed Avro placement contract without lossy normalization. */
public final class BetPlacedMapper {

  public BetPlacement map(BetPlacedRequested event) {
    try {
      return new BetPlacement(
          canonicalUuid(event.getBetId(), "betId"),
          canonicalUuid(event.getUserId(), "userId"),
          slipType(event),
          new Money(
              event.getStake().getAmount(),
              Currency.valueOf(event.getStake().getCurrency().toString())),
          event.getRequestedAt(),
          event.getSelections().stream().map(this::selection).toList());
    } catch (PlacementContractException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new PlacementContractException(
          "Invalid BetPlacedRequested field: " + exception.getMessage());
    }
  }

  private BetPlacement.Selection selection(RequestedSelection selection) {
    String encodedOdds = selection.getOddsAtSubmission().toString();
    Odds odds = Odds.ofDecimal(new BigDecimal(encodedOdds));
    if (!odds.decimal().toPlainString().equals(encodedOdds)) {
      throw new PlacementContractException("oddsAtSubmission must use canonical scale 4");
    }
    return new BetPlacement.Selection(
        canonicalUuid(selection.getEventId(), "eventId"),
        canonicalUuid(selection.getMarketId(), "marketId"),
        canonicalUuid(selection.getSelectionId(), "selectionId"),
        odds);
  }

  private static BetSlipType slipType(BetPlacedRequested event) {
    Integer minimumWins = event.getSystemMinWins();
    Integer totalSelections = event.getSystemTotalSelections();
    if (event.getSlipType() == BetSlipTypeTag.SYSTEM) {
      if (minimumWins == null || totalSelections == null) {
        throw new PlacementContractException("SYSTEM requires K and N");
      }
      return new BetSlipType.System(minimumWins, totalSelections);
    }
    if (minimumWins != null || totalSelections != null) {
      throw new PlacementContractException("Non-system slip must omit K and N");
    }
    return event.getSlipType() == BetSlipTypeTag.SINGLE
        ? new BetSlipType.Single()
        : new BetSlipType.Multiple();
  }

  private static UUID canonicalUuid(CharSequence encoded, String field) {
    if (encoded == null) {
      throw new PlacementContractException(field + " is required");
    }
    String text = encoded.toString();
    UUID parsed = UUID.fromString(text);
    if (!parsed.toString().equals(text)) {
      throw new PlacementContractException(field + " must be canonical lowercase UUID text");
    }
    return parsed;
  }
}
