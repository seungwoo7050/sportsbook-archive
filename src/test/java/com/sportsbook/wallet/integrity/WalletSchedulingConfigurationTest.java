package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class WalletSchedulingConfigurationTest {

  @Test
  void runsScheduledWalletWorkersConcurrently() {
    ApplicationContextRunner context =
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfiguration.class);

    context.run(
        running -> {
          ThreadPoolTaskScheduler scheduler = running.getBean(ThreadPoolTaskScheduler.class);
          CountDownLatch started = new CountDownLatch(4);
          CountDownLatch release = new CountDownLatch(1);
          Runnable worker = () -> awaitRelease(started, release);
          scheduler.execute(worker);
          scheduler.execute(worker);
          scheduler.execute(worker);
          scheduler.execute(worker);
          try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("wallet-scheduler-");
          } finally {
            release.countDown();
          }
        });
  }

  private static void awaitRelease(CountDownLatch started, CountDownLatch release) {
    started.countDown();
    try {
      release.await(2, TimeUnit.SECONDS);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("worker interrupted", failure);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  static class SchedulingConfiguration {}
}
