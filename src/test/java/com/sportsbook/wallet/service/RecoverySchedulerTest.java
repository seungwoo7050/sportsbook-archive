package com.sportsbook.wallet.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class RecoverySchedulerTest {
  @Test
  void delegatesOneBoundedAttemptPerPoll() {
    RecoveryWorker worker = mock(RecoveryWorker.class);

    new RecoveryScheduler(worker).poll();

    verify(worker).recoverOne();
  }
}
