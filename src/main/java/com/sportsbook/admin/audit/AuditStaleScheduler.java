package com.sportsbook.admin.audit;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AuditStaleScheduler {

  private static final Logger log = LoggerFactory.getLogger(AuditStaleScheduler.class);
  private static final String CLAIMED_METRIC = "admin.audit.stale.claimed";
  private static final String FAILURE_METRIC = "admin.audit.stale.scan.failure";

  private final AuditWriteRepository auditWrites;
  private final AdminActionPublisher publisher;
  private final MeterRegistry meters;
  private final Duration staleAfter;
  private final int batchSize;

  public AuditStaleScheduler(
      AuditWriteRepository auditWrites,
      AdminActionPublisher publisher,
      MeterRegistry meters,
      @Value("${admin.audit.stale-after:PT5M}") Duration staleAfter,
      @Value("${admin.audit.stale-batch-size:100}") int batchSize) {
    this.auditWrites = auditWrites;
    this.publisher = publisher;
    this.meters = meters;
    this.staleAfter = staleAfter;
    this.batchSize = batchSize;
  }

  @Scheduled(
      initialDelayString = "${admin.audit.stale-scan-interval:PT30S}",
      fixedDelayString = "${admin.audit.stale-scan-interval:PT30S}")
  void scan() {
    try {
      List<AuditTerminalRecord> claimed = auditWrites.claimStale(staleAfter, batchSize);
      claimed.forEach(publisher::publish);
      meters.counter(CLAIMED_METRIC).increment(claimed.size());
    } catch (RuntimeException failure) {
      meters.counter(FAILURE_METRIC).increment();
      log.error("Failed to claim stale audit actions", failure);
    }
  }
}
