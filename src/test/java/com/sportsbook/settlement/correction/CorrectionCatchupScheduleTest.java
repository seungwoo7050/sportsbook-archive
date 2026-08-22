package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class CorrectionCatchupScheduleTest {

  @Test
  void usesDedicatedCorrectionScheduler() throws Exception {
    Scheduled scheduled =
        CorrectionCatchupScanner.class.getMethod("catchUp").getAnnotation(Scheduled.class);

    assertThat(scheduled.scheduler()).isEqualTo(SettlementWorkerConfiguration.CORRECTION);
    assertThat(scheduled.fixedDelayString())
        .isEqualTo("${settlement.runtime.recovery-interval:PT1S}");
  }
}
