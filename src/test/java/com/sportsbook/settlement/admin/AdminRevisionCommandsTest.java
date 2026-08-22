package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRevisionCommandsTest {

  @Test
  void reportsQueuedThenReplayWithoutCallingWallet() {
    AdminRevisionRetry retry = mock(AdminRevisionRetry.class);
    AdminRevisionQueryRepository revisions = mock(AdminRevisionQueryRepository.class);
    UUID key = UUID.randomUUID();
    UUID revisionId = UUID.randomUUID();
    Instant due = Instant.parse("2026-08-22T00:00:00Z");
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
    when(revisions.find(revisionId)).thenReturn(Optional.of(view(revisionId, due)));
    AdminRevisionCommands commands = new AdminRevisionCommands(retry, revisions);
    when(retry.claim(key, revisionId))
        .thenReturn(
            new AdminRevisionRetry.Decision(action, false),
            new AdminRevisionRetry.Decision(action, true));

    assertThat(commands.retry(key, revisionId))
        .isEqualTo(new AdminRevisionCommands.Receipt(key, "QUEUED", "PENDING", 0, due));
    assertThat(commands.retry(key, revisionId).outcome()).isEqualTo("REPLAY");
  }

  private static AdminRevisionQueryRepository.View view(UUID revisionId, Instant due) {
    return new AdminRevisionQueryRepository.View(
        revisionId,
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "PENDING",
        0,
        due,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.EPOCH,
        Instant.EPOCH,
        null);
  }
}
