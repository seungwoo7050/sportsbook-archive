package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WalletOperationProofTest {

  private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000102");
  private static final UUID GROUP = UUID.fromString("018f0000-0000-7000-8000-000000000103");
  private static final Money AMOUNT = Money.krw(750);
  private static final Instant AT = Instant.parse("2026-08-22T01:02:03Z");
  private static final WalletCreditPayload REQUEST = WalletCreditPayload.refund(USER, AMOUNT);

  @Test
  void acceptsOnlyTheMatchingAuthoritativeProof() {
    assertThat(WalletOperationProof.verifyRefund(REQUEST, valid())).isEqualTo(GROUP);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidProofs")
  void rejectsMalformedOrMismatchedSuccess(String label, WalletOperationResponse response) {
    assertThatThrownBy(() -> WalletOperationProof.verifyRefund(REQUEST, response))
        .isInstanceOf(DownstreamContractException.class)
        .hasMessageContaining("Wallet refund proof");
  }

  private static Stream<Arguments> invalidProofs() {
    return Stream.of(
        Arguments.of("missing body", null),
        Arguments.of("missing operation group", response(null, USER, AMOUNT, "BET_REFUND", AT)),
        Arguments.of("missing user", response(GROUP, null, AMOUNT, "BET_REFUND", AT)),
        Arguments.of(
            "wrong user",
            response(
                GROUP,
                UUID.fromString("018f0000-0000-7000-8000-000000000104"),
                AMOUNT,
                "BET_REFUND",
                AT)),
        Arguments.of("missing amount", response(GROUP, USER, null, "BET_REFUND", AT)),
        Arguments.of("wrong amount", response(GROUP, USER, Money.krw(751), "BET_REFUND", AT)),
        Arguments.of("wrong currency", response(GROUP, USER, Money.usd(750), "BET_REFUND", AT)),
        Arguments.of("request reason echoed", response(GROUP, USER, AMOUNT, "REFUND", AT)),
        Arguments.of("missing reason", response(GROUP, USER, AMOUNT, null, AT)),
        Arguments.of("missing timestamp", response(GROUP, USER, AMOUNT, "BET_REFUND", null)));
  }

  private static WalletOperationResponse valid() {
    return response(GROUP, USER, AMOUNT, "BET_REFUND", AT);
  }

  private static WalletOperationResponse response(
      UUID group, UUID user, Money amount, String reason, Instant at) {
    return new WalletOperationResponse(group, user, amount, reason, at);
  }
}
