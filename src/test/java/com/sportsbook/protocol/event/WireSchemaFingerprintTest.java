package com.sportsbook.protocol.event;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.junit.jupiter.api.Test;

class WireSchemaFingerprintTest {

  private static final String NAMESPACE = "com.sportsbook.protocol.event.";
  private static final Map<String, String> EXPECTED =
      Map.ofEntries(
          entry(NAMESPACE + "Money", "0ca10968275dbdf4"),
          entry(NAMESPACE + "BetSettled", "113bc9d5037a850c"),
          entry(NAMESPACE + "BetPlacedRequested", "0de97e269b734cc8"),
          entry(NAMESPACE + "BetResolutionRevised", "b05cdf4b95651059"),
          entry(NAMESPACE + "BetVoided", "ae914f69f90cd749"),
          entry(NAMESPACE + "EventLifecycle", "e47d6dbd952bc721"),
          entry(NAMESPACE + "MarketStatusChanged", "b0de225c89d1303a"),
          entry(NAMESPACE + "MatchResult", "3f39fbc4bbfea727"),
          entry(NAMESPACE + "OddsChanged", "4378ea6ef79a8d95"),
          entry(NAMESPACE + "RiskLimitViolated", "0b63e276e0c81cfb"),
          entry(NAMESPACE + "RiskPatternSuspected", "540ef62587fd31b0"),
          entry(NAMESPACE + "WalletCredited", "31bca64c5a3a52a1"),
          entry(NAMESPACE + "WalletDebitFailed", "10d0317115d8a749"),
          entry(NAMESPACE + "WalletDebited", "eed98e2d2702dfe7"));

  @Test
  void wireV1CanonicalFingerprintsAreStable() throws Exception {
    Map<String, String> actual =
        WireSchemaTestSupport.loadSchemas().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        String.format(
                            Locale.ROOT,
                            "%016x",
                            SchemaNormalization.parsingFingerprint64(entry.getValue()))));
    assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED);
  }

  @Test
  void optionalFieldsHaveExplicitNullDefaults() throws Exception {
    Set<String> defaulted =
        WireSchemaTestSupport.loadSchemas().values().stream()
            .flatMap(schema -> schema.getFields().stream().map(field -> fieldId(schema, field)))
            .filter(FieldContract::defaulted)
            .map(FieldContract::id)
            .collect(Collectors.toSet());
    assertThat(defaulted)
        .containsExactlyInAnyOrder(
            "BetPlacedRequested.systemMinWins",
            "BetPlacedRequested.systemTotalSelections",
            "BetSettled.resultDetail",
            "MarketStatusChanged.reason");
  }

  @Test
  void temporalFieldsUseMillisecondInstants() throws Exception {
    Set<String> timestamps =
        WireSchemaTestSupport.loadSchemas().values().stream()
            .flatMap(schema -> schema.getFields().stream().map(field -> fieldId(schema, field)))
            .filter(FieldContract::timestampMillis)
            .map(FieldContract::id)
            .collect(Collectors.toSet());
    assertThat(timestamps)
        .containsExactlyInAnyOrder(
            "BetPlacedRequested.requestedAt",
            "BetResolutionRevised.sourceResultSettledAt",
            "BetResolutionRevised.revisedAt",
            "BetSettled.settledAt",
            "BetVoided.voidedAt",
            "EventLifecycle.occurredAt",
            "EventLifecycle.scheduledStartAt",
            "MarketStatusChanged.occurredAt",
            "MatchResult.settledAt",
            "OddsChanged.changedAt",
            "RiskLimitViolated.occurredAt",
            "RiskPatternSuspected.occurredAt",
            "WalletCredited.occurredAt",
            "WalletDebitFailed.occurredAt",
            "WalletDebited.occurredAt");
  }

  private static FieldContract fieldId(Schema owner, Schema.Field field) {
    return new FieldContract(owner.getName() + "." + field.name(), field);
  }

  private record FieldContract(String id, Schema.Field field) {
    boolean defaulted() {
      return field.hasDefaultValue();
    }

    boolean timestampMillis() {
      return field.schema().getLogicalType() != null
          && field.schema().getLogicalType().getName().equals("timestamp-millis");
    }
  }
}
