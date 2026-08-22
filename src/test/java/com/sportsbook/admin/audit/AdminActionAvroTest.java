package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.event.AdminActionRecorded;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;

class AdminActionAvroTest {

  @Test
  void pinsTheTerminalSchemaFingerprint() {
    assertThat(SchemaNormalization.parsingFingerprint64(AdminActionRecorded.getClassSchema()))
        .isEqualTo(467411456356349815L);
  }

  @Test
  void roundTripsSuccessAndUnknownWithNullableStatus() throws IOException {
    Instant started = Instant.parse("2026-08-22T01:02:03.004Z");
    Instant completed = started.plusSeconds(5);
    AdminActionRecorded success =
        baseBuilder(started, completed)
            .setOutcome("SUCCESS")
            .setHttpStatus(202)
            .setReason("operator request")
            .build();
    AdminActionRecorded unknown =
        baseBuilder(started, completed)
            .setOutcome("UNKNOWN")
            .setHttpStatus(null)
            .setReason(null)
            .build();

    assertThat(roundTrip(success)).isEqualTo(success);
    assertThat(roundTrip(unknown)).isEqualTo(unknown);
    assertThat(roundTrip(unknown).getHttpStatus()).isNull();
    assertThat(roundTrip(success).getStartedAt()).isEqualTo(started);
    assertThat(roundTrip(success).getCompletedAt()).isEqualTo(completed);
  }

  private static AdminActionRecorded.Builder baseBuilder(Instant started, Instant completed) {
    return AdminActionRecorded.newBuilder()
        .setActionId("018f0000-0000-7000-8000-000000000091")
        .setActorId("operator-1")
        .setActorRole("ADMIN")
        .setAction("MARKET_CLOSE")
        .setTarget("market-1")
        .setTraceId("0123456789abcdef0123456789abcdef")
        .setStartedAt(started)
        .setCompletedAt(completed);
  }

  private static AdminActionRecorded roundTrip(AdminActionRecorded source) throws IOException {
    var reader = new SpecificDatumReader<AdminActionRecorded>(source.getSchema());
    var decoder =
        DecoderFactory.get()
            .binaryDecoder(new ByteArrayInputStream(AvroSerializer.toBytes(source)), null);
    return reader.read(null, decoder);
  }
}
