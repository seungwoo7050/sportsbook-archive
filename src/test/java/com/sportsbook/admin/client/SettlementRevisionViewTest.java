package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRevisionViewTest {

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void deserializesAllNineteenTypedEvidenceFields() throws Exception {
    SettlementRevisionView view =
        json.readValue(
            """
            {
              "revisionId":"018f0000-0000-7000-8000-000000000144",
              "betId":"018f0000-0000-7000-8000-000000000145",
              "revisionNumber":2,
              "eventId":"018f0000-0000-7000-8000-000000000146",
              "sourceCandidateId":"018f0000-0000-7000-8000-000000000147",
              "state":"BLOCKED","attemptCount":3,
              "nextRetryAt":"2026-08-22T01:03:00Z","lastErrorCode":"WALLET_BUSY",
              "leaseUntil":null,"walletStatus":"BLOCKED","walletQueueSequence":17,
              "walletOperationGroupId":"018f0000-0000-7000-8000-000000000148",
              "walletQueuedAt":"2026-08-22T01:01:00Z","walletAppliedAt":null,
              "walletNextAttemptAt":"2026-08-22T01:03:00Z",
              "createdAt":"2026-08-22T01:00:00Z","updatedAt":"2026-08-22T01:02:00Z",
              "appliedAt":null
            }
            """,
            SettlementRevisionView.class);

    assertThat(view.revisionId())
        .isEqualTo(UUID.fromString("018f0000-0000-7000-8000-000000000144"));
    assertThat(view.revisionNumber()).isEqualTo(2);
    assertThat(view.state()).isEqualTo(SettlementRevisionView.State.BLOCKED);
    assertThat(view.attemptCount()).isEqualTo(3);
    assertThat(view.walletStatus()).isEqualTo(SettlementRevisionView.WalletStatus.BLOCKED);
    assertThat(view.walletQueueSequence()).isEqualTo(17);
    assertThat(view.nextRetryAt()).isEqualTo(Instant.parse("2026-08-22T01:03:00Z"));
    assertThat(view.appliedAt()).isNull();
  }
}
