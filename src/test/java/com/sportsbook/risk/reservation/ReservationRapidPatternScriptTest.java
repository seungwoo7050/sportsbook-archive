package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReservationRapidPatternScriptTest extends ReservationScriptTestSupport {
  @Test
  void preventsParallelCandidatesFromBypassingTheRapidThreshold() throws Exception {
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofMinutes(1), 5, PatternAction.BLOCK),
            SuddenStakePolicy.defaults(),
            RepeatedSelectionPolicy.defaults());
    var executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<ReservationDecision>> futures =
          IntStream.range(0, 20)
              .mapToObj(
                  index ->
                      executor.submit(
                          () -> {
                            start.await();
                            return execute(
                                    request(
                                        command(
                                            200 + index, 10, Currency.KRW, selection(200 + index)),
                                        limits(1_000),
                                        patterns))
                                .decision();
                          }))
              .toList();
      start.countDown();

      List<ReservationDecision> decisions =
          futures.stream().map(ReservationRapidPatternScriptTest::result).toList();
      assertThat(decisions.stream().filter(ReservationDecision::approved)).hasSize(4);
      assertThat(decisions.stream().filter(decision -> !decision.approved()))
          .allMatch(decision -> "RAPID_BETTING".equals(decision.rejection()));
      assertThat(redis.opsForZSet().size(ReservationKeys.activeBets(USER))).isEqualTo(4);
    } finally {
      executor.shutdownNow();
    }
  }

  private static ReservationDecision result(Future<ReservationDecision> future) {
    try {
      return future.get();
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }
}
