package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRevisionRetryReplayTest {

  @Test
  void returnsTheOriginalOwnerWithoutResettingAttemptsAgain() {
    AdminActionRepository actions = mock(AdminActionRepository.class);
    AdminRevisionRetryRepository retries = mock(AdminRevisionRetryRepository.class);
    AdminRevisionQueryRepository revisions = mock(AdminRevisionQueryRepository.class);
    UUID key = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    AdminAction action =
        new AdminAction(
            key,
            AdminAction.Kind.REVISION_RETRY,
            revisionId,
            AdminRequestFingerprint.create(AdminAction.Kind.REVISION_RETRY, revisionId, ""),
            AdminAction.Outcome.REVISION_RETRY_QUEUED,
            UUID.randomUUID(),
            Instant.EPOCH,
            Instant.EPOCH);
    when(actions.lockAndFind(key)).thenReturn(Optional.of(action));
    var retry = new AdminRevisionRetry(actions, retries, revisions);

    assertThat(retry.claim(key, revisionId))
        .isEqualTo(new AdminRevisionRetry.Decision(action, true));
    verifyNoInteractions(retries, revisions);
  }
}
