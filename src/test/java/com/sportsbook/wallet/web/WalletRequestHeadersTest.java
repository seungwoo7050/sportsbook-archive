package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WalletRequestHeadersTest {
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000431");

  @Test
  void readsExactlyOneIdempotencyKeyWithoutNormalization() {
    MockHttpServletRequest request = request("deposit: exact identity");

    assertThat(WalletRequestHeaders.requireIdempotencyKey(request))
        .isEqualTo(IdempotencyKey.of("deposit: exact identity"));
  }

  @Test
  void rejectsMissingDuplicateAndMalformedKeys() {
    MockHttpServletRequest duplicate = request("first");
    duplicate.addHeader(WalletRequestHeaders.IDEMPOTENCY_KEY, "second");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> WalletRequestHeaders.requireIdempotencyKey(new MockHttpServletRequest()))
        .withMessage("Exactly one Idempotency-Key header is required");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> WalletRequestHeaders.requireIdempotencyKey(duplicate))
        .withMessage("Exactly one Idempotency-Key header is required");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> WalletRequestHeaders.requireIdempotencyKey(request(" ")))
        .withMessage("IdempotencyKey must not be blank");
    assertThatNullPointerException()
        .isThrownBy(() -> WalletRequestHeaders.requireIdempotencyKey(null));
  }

  @Test
  void acceptsOnlyCanonicalLowercaseDebitIdentities() {
    assertThat(WalletRequestHeaders.requireCanonicalDebitKey(request(BET_ID.toString())))
        .isEqualTo(IdempotencyKey.of(BET_ID.toString()));
    assertThat(WalletRequestHeaders.requireCanonicalDebitId(BET_ID.toString())).isEqualTo(BET_ID);

    for (String invalid :
        List.of(
            BET_ID.toString().toUpperCase(Locale.ROOT),
            "1-1-1-1-1",
            " " + BET_ID,
            "not-a-debit-id")) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> WalletRequestHeaders.requireCanonicalDebitId(invalid))
          .withMessage("Debit identity must be a canonical UUID");
    }
    assertThatNullPointerException()
        .isThrownBy(() -> WalletRequestHeaders.requireCanonicalDebitId(null));
  }

  private MockHttpServletRequest request(String value) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(WalletRequestHeaders.IDEMPOTENCY_KEY, value);
    return request;
  }
}
