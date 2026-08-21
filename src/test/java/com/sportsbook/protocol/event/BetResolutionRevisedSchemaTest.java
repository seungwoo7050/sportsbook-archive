package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

class BetResolutionRevisedSchemaTest {

  @Test
  void revisionUsesExactRequiredFields() {
    Schema schema = BetResolutionRevised.getClassSchema();
    AvroRecordTestSupport.assertFields(
        schema,
        "revisionId",
        "revisionNumber",
        "betId",
        "userId",
        "eventId",
        "previousResult",
        "newResult",
        "previousPayout",
        "newPayout",
        "sourceResultSettledAt",
        "revisedAt");
    assertThat(schema.getFields()).allMatch(field -> !field.hasDefaultValue());
  }

  @Test
  void revisionReusesSettlementAndMoneyTypes() {
    Schema schema = BetResolutionRevised.getClassSchema();
    assertThat(schema.getField("previousResult").schema().getFullName())
        .isEqualTo("com.sportsbook.protocol.event.SettlementResultAvro");
    assertThat(schema.getField("newResult").schema())
        .isEqualTo(schema.getField("previousResult").schema());
    assertThat(schema.getField("previousPayout").schema().getFullName())
        .isEqualTo("com.sportsbook.protocol.event.Money");
    assertThat(schema.getField("newPayout").schema())
        .isEqualTo(schema.getField("previousPayout").schema());
  }

  @Test
  void revisionTimestampsUseMillisLogicalType() {
    Schema schema = BetResolutionRevised.getClassSchema();
    assertThat(schema.getField("sourceResultSettledAt").schema().getLogicalType().getName())
        .isEqualTo("timestamp-millis");
    assertThat(schema.getField("revisedAt").schema().getLogicalType().getName())
        .isEqualTo("timestamp-millis");
  }
}
