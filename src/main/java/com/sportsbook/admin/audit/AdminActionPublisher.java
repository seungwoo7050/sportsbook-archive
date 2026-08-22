package com.sportsbook.admin.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminActionPublisher {

  private static final Logger log = LoggerFactory.getLogger(AdminActionPublisher.class);
  private static final String FAILURE_METRIC = "admin.audit.publish.failure";

  private final KafkaTemplate<String, byte[]> kafka;
  private final MeterRegistry meters;
  private final String topic;

  public AdminActionPublisher(
      KafkaTemplate<String, byte[]> auditKafkaTemplate,
      MeterRegistry meters,
      @Value("${admin.audit.topic:admin.action}") String topic) {
    this.kafka = auditKafkaTemplate;
    this.meters = meters;
    this.topic = topic;
  }

  public void publish(AuditTerminalRecord record) {
    try {
      byte[] value = AvroSerializer.toBytes(AuditEventMapper.toEvent(record));
      kafka
          .send(topic, record.actorId(), value)
          .whenComplete(
              (ignored, failure) -> {
                if (failure != null) {
                  recordFailure(record, failure);
                }
              });
    } catch (RuntimeException failure) {
      recordFailure(record, failure);
    }
  }

  private void recordFailure(AuditTerminalRecord record, Throwable failure) {
    meters.counter(FAILURE_METRIC).increment();
    log.error(
        "Failed to publish terminal audit action {} ({})",
        record.actionId(),
        record.action(),
        failure);
  }
}
