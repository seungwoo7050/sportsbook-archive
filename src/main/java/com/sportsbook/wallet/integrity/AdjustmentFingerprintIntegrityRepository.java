package com.sportsbook.wallet.integrity;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.service.OperationFingerprint;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Recomputes canonical adjustment identity without duplicating its binary encoder in SQL. */
@Repository
public class AdjustmentFingerprintIntegrityRepository {
  private static final String IDENTITIES_SQL =
      """
      SELECT a.idempotency_key, a.revision_id, a.bet_id, a.revision_number, a.user_id,
        a.previous_payout_amount, a.new_payout_amount, a.currency, o.request_fingerprint
      FROM wallet_adjustment a
      JOIN wallet_operation o ON o.idempotency_key = a.idempotency_key
      ORDER BY a.idempotency_key
      """;

  private final JdbcTemplate jdbc;

  public AdjustmentFingerprintIntegrityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> findFingerprintDriftKeys() {
    return jdbc
        .query(
            IDENTITIES_SQL,
            (row, number) ->
                new AdjustmentIdentity(
                    row.getString("idempotency_key"),
                    row.getObject("revision_id", UUID.class),
                    row.getObject("bet_id", UUID.class),
                    row.getLong("revision_number"),
                    row.getObject("user_id", UUID.class),
                    row.getLong("previous_payout_amount"),
                    row.getLong("new_payout_amount"),
                    Currency.valueOf(row.getString("currency")),
                    row.getString("request_fingerprint")))
        .stream()
        .filter(identity -> !identity.hasCanonicalFingerprint())
        .map(AdjustmentIdentity::key)
        .toList();
  }

  private record AdjustmentIdentity(
      String key,
      UUID revisionId,
      UUID betId,
      long revisionNumber,
      UUID userId,
      long previousPayout,
      long newPayout,
      Currency currency,
      String storedFingerprint) {
    private boolean hasCanonicalFingerprint() {
      return OperationFingerprint.adjustment(
              WalletCaller.SETTLEMENT,
              userId,
              new Money(previousPayout, currency),
              new Money(newPayout, currency),
              revisionId,
              betId,
              revisionNumber)
          .value()
          .equals(storedFingerprint);
    }
  }
}
