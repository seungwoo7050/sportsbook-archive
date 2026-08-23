package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.event.AdminActionRecorded;
import com.sportsbook.admin.security.AdminRole;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;

class AdminActionPublisherRealKafkaTest extends RealKafkaAuditFixture {

  @Test
  void publishesACompleteRawAvroTerminalEvent() throws IOException {
    Instant started = Instant.parse("2026-08-22T01:02:03.123Z");
    Instant completed = Instant.parse("2026-08-22T01:02:04.456Z");
    AuditTerminalRecord terminal =
        new AuditTerminalRecord(
            UUID.fromString("018f0000-0000-7000-8000-000000000093"),
            "operator-real-kafka",
            AdminRole.TRADER,
            "MARKET_CLOSE",
            "event-1/market-4",
            AuditOutcome.UNKNOWN,
            null,
            "downstream outcome unknown",
            "7f6d55f9e7e2482190f9ed6647e9d62b",
            started,
            completed);

    publish(terminal);

    var record = consumeOne();
    AdminActionRecorded event = decode(record.value());
    assertThat(record.topic()).isEqualTo(TOPIC);
    assertThat(record.key()).isEqualTo(terminal.actorId());
    assertThat(event.getActionId()).isEqualTo(terminal.actionId().toString());
    assertThat(event.getActorId()).isEqualTo(terminal.actorId());
    assertThat(event.getActorRole()).isEqualTo(terminal.actorRole().name());
    assertThat(event.getAction()).isEqualTo(terminal.action());
    assertThat(event.getTarget()).isEqualTo(terminal.target());
    assertThat(event.getOutcome()).isEqualTo(terminal.outcome().name());
    assertThat(event.getHttpStatus()).isNull();
    assertThat(event.getReason()).isEqualTo(terminal.reason());
    assertThat(event.getTraceId()).isEqualTo(terminal.traceId());
    assertThat(event.getStartedAt()).isEqualTo(started);
    assertThat(event.getCompletedAt()).isEqualTo(completed);
    assertThat(SchemaNormalization.parsingFingerprint64(event.getSchema()))
        .isEqualTo(467411456356349815L);
  }

  private static AdminActionRecorded decode(byte[] value) throws IOException {
    var reader = new SpecificDatumReader<AdminActionRecorded>(AdminActionRecorded.class);
    return reader.read(null, DecoderFactory.get().binaryDecoder(value, null));
  }
}
