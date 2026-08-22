package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRevisionRetryTest {

  @Test
  void atomicallyStoresTheDispatchTokenForAQueuedRetry() {
    AdminActionRepository actions = mock(AdminActionRepository.class);
    AdminRevisionRetryRepository retries = mock(AdminRevisionRetryRepository.class);
    AdminRevisionQueryRepository revisions = mock(AdminRevisionQueryRepository.class);
    UUID key = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    String fingerprint =
        AdminRequestFingerprint.create(AdminAction.Kind.REVISION_RETRY, revisionId, "");
    when(actions.lockAndFind(key)).thenReturn(Optional.empty());
    when(retries.queue(revisionId))
        .thenReturn(
            Optional.of(new AdminRevisionRetryRepository.Queued("PENDING", false, Instant.EPOCH)));
    when(actions.append(any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                new AdminAction(
                    key,
                    AdminAction.Kind.REVISION_RETRY,
                    revisionId,
                    fingerprint,
                    AdminAction.Outcome.REVISION_RETRY_QUEUED,
                    invocation.getArgument(5),
                    Instant.EPOCH,
                    Instant.EPOCH));
    var retry = new AdminRevisionRetry(actions, retries, revisions);

    AdminRevisionRetry.Decision decision = retry.claim(key, revisionId);

    assertThat(decision.replay()).isFalse();
    assertThat(decision.action().executionToken()).isNotNull();
  }
}
