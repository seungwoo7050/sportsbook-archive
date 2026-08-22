package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RevisionRecoveryScheduleTest {

  @Test
  void runsOnTheIsolatedRecoveryScheduler() throws NoSuchMethodException {
    Scheduled scheduled =
        RevisionRecoveryScanner.class.getMethod("recover").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo(SettlementWorkerConfiguration.RECOVERY);
    assertThat(scheduled.fixedDelayString())
        .isEqualTo("${settlement.runtime.recovery-interval:PT1S}");
  }
}
