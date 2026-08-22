package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementCandidateReceiptTest {

  private static final UUID KEY = UUID.fromString("018f0000-0000-7000-8000-000000000161");

  @Test
  void acceptsMatchingApprovalAndRejectionReceipts() {
    SettlementCandidateReceipt approval =
        new SettlementCandidateReceipt(
            KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, false);
    SettlementCandidateReceipt rejection =
        new SettlementCandidateReceipt(
            KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_REJECTED, true);

    assertThat(
            SettlementCandidateReceipt.verify(
                KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, approval))
        .isSameAs(approval);
    assertThat(
            SettlementCandidateReceipt.verify(
                KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_REJECTED, rejection))
        .isSameAs(rejection);
  }

  @Test
  void rejectsMissingMismatchedAndIncompleteReceipts() {
    UUID other = UUID.fromString("018f0000-0000-7000-8000-000000000162");

    assertInvalid(null);
    assertInvalid(
        new SettlementCandidateReceipt(
            other, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, false));
    assertInvalid(
        new SettlementCandidateReceipt(
            KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_REJECTED, false));
    assertInvalid(
        new SettlementCandidateReceipt(
            KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, null));
  }

  private static void assertInvalid(SettlementCandidateReceipt receipt) {
    assertThatThrownBy(
            () ->
                SettlementCandidateReceipt.verify(
                    KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, receipt))
        .isInstanceOf(DownstreamContractException.class);
  }
}
