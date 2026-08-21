package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.policy.RiskPatternProperties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReservationRollingCapacityScriptTest extends ReservationScriptTestSupport {
  @Test
  void isolatesActiveCapacityByCurrency() {
    RiskPatternProperties patterns = new RiskPatternProperties(null, null, null);

    assertThat(
            execute(request(command(30, 60, Currency.KRW), limits(100), patterns))
                .decision()
                .approved())
        .isTrue();
    assertThat(
            execute(request(command(31, 50, Currency.KRW), limits(100), patterns))
                .decision()
                .rejection())
        .isEqualTo("STAKE_DAILY_LIMIT_EXCEEDED");
    assertThat(
            execute(request(command(32, 50, Currency.USD), limits(100), patterns))
                .decision()
                .approved())
        .isTrue();
  }

  @Test
  void serializesParallelRequestsAtTheLastAvailableCapacity() throws Exception {
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
                                            100 + index, 10, Currency.KRW, selection(100 + index)),
                                        limits(100),
                                        new RiskPatternProperties(null, null, null)))
                                .decision();
                          }))
              .toList();
      start.countDown();

      List<ReservationDecision> decisions =
          futures.stream().map(ReservationRollingCapacityScriptTest::result).toList();
      assertThat(decisions.stream().filter(ReservationDecision::approved)).hasSize(10);
      assertThat(decisions.stream().filter(decision -> !decision.approved()))
          .allMatch(decision -> "STAKE_DAILY_LIMIT_EXCEEDED".equals(decision.rejection()));
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
