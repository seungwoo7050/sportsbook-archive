package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryKeysTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId FIRST = SelectionId.of(new UUID(0, 3));
  private static final SelectionId SECOND = SelectionId.of(new UUID(0, 4));

  @Test
  void scopesStakeHistoryByCurrencyAndOtherFactsGlobally() {
    assertThat(HistoryKeys.bets(USER)).endsWith("}:bets");
    assertThat(HistoryKeys.stakes(USER, Currency.KRW)).endsWith(":stakes:krw");
    assertThat(HistoryKeys.stakes(USER, Currency.USD)).endsWith(":stakes:usd");
    assertThat(HistoryKeys.selection(USER, FIRST))
        .isNotEqualTo(HistoryKeys.selection(USER, SECOND));
    assertThat(HistoryKeys.selection(USER, FIRST)).doesNotContain("krw", "usd");
  }

  @Test
  void preservesUuidIdentityInUnambiguousMembers() {
    assertThat(HistoryKeys.betMember(BET)).isEqualTo(BET.value().toString());
    assertThat(HistoryKeys.stakeMember(BET, 250)).isEqualTo(BET.value().toString() + "|250");
    assertThat(HistoryKeys.bets(USER)).contains("{" + USER.value() + "}");
    assertThat(HistoryKeys.stakes(USER, Currency.KRW)).contains("{" + USER.value() + "}");
  }
}
