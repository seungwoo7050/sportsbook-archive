package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.outbox.LeasedOutboxMessage;
import com.sportsbook.wallet.outbox.OutboxBacklogSnapshot;
import com.sportsbook.wallet.outbox.OutboxLease;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxDeliveryRepository {

  private static final int MAX_ERROR_LENGTH = 1024;

  private static final String PUBLISH_SQL =
      """
      WITH db_clock AS MATERIALIZED (SELECT clock_timestamp() AS now)
      UPDATE outbox_event e
      SET published_at = c.now,
          lease_owner = NULL, lease_until = NULL, last_error = NULL
      FROM db_clock c
      WHERE e.event_id = :eventId AND e.lease_owner = :owner
        AND e.lease_version = :version AND e.published_at IS NULL
      """;

  private static final String RETRY_SQL =
      """
      WITH db_clock AS MATERIALIZED (SELECT clock_timestamp() AS now)
      UPDATE outbox_event e
      SET available_at = c.now + CAST(:delayMillis AS bigint) * interval '1 millisecond',
          lease_owner = NULL, lease_until = NULL, last_error = :error
      FROM db_clock c
      WHERE e.event_id = :eventId AND e.lease_owner = :owner
        AND e.lease_version = :version AND e.published_at IS NULL
      """;

  private static final String CLAIM_SQL =
      """
      WITH db_clock AS MATERIALIZED (
          SELECT clock_timestamp() AS now
      ), candidates AS MATERIALIZED (
          SELECT e.event_id, e.lease_owner IS NOT NULL AS lease_takeover
          FROM outbox_event e CROSS JOIN db_clock c
          WHERE e.published_at IS NULL
            AND e.available_at <= c.now
            AND (e.lease_until IS NULL OR e.lease_until <= c.now)
            AND NOT EXISTS (
                SELECT 1 FROM outbox_event older
                WHERE older.topic = e.topic
                  AND older.partition_key = e.partition_key
                  AND older.published_at IS NULL
                  AND older.stream_sequence < e.stream_sequence
            )
          ORDER BY e.available_at, e.created_at, e.event_id
          FOR UPDATE OF e SKIP LOCKED
          LIMIT :batchSize
      )
      UPDATE outbox_event e
      SET lease_owner = :owner,
          lease_until = c.now + CAST(:leaseMillis AS bigint) * interval '1 millisecond',
          lease_version = e.lease_version + 1,
          attempt_count = e.attempt_count + 1
      FROM candidates candidate CROSS JOIN db_clock c
      WHERE e.event_id = candidate.event_id
      RETURNING e.event_id, e.topic, e.partition_key, e.schema_name, e.payload,
                e.stream_sequence, candidate.lease_takeover, e.attempt_count, e.created_at,
                e.lease_owner, e.lease_version, e.lease_until
      """;

  private static final String SNAPSHOT_SQL =
      """
      WITH db_clock AS MATERIALIZED (SELECT clock_timestamp() AS now)
      SELECT COUNT(*) FILTER (WHERE e.published_at IS NULL) AS pending_count,
             COUNT(*) FILTER (
                 WHERE e.published_at IS NULL AND e.lease_until > c.now) AS leased_count,
             GREATEST(COALESCE(EXTRACT(EPOCH FROM (
                 MAX(c.now) - MIN(e.created_at) FILTER (WHERE e.published_at IS NULL))), 0), 0)
                 AS oldest_pending_seconds
      FROM outbox_event e CROSS JOIN db_clock c
      """;

  private static final Comparator<LeasedOutboxMessage> DELIVERY_ORDER =
      Comparator.comparing(LeasedOutboxMessage::createdAt)
          .thenComparing(message -> message.lease().eventId());

  private final NamedParameterJdbcTemplate jdbc;

  public OutboxDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public List<LeasedOutboxMessage> claim(String owner, int batchSize, Duration leaseDuration) {
    if (owner == null || owner.isBlank()) {
      throw new IllegalArgumentException("owner must not be blank");
    }
    if (batchSize < 1 || leaseDuration.toMillis() < 1L) {
      throw new IllegalArgumentException("batch size and lease duration must be positive");
    }
    Map<String, Object> parameters =
        Map.of(
            "owner", owner,
            "batchSize", batchSize,
            "leaseMillis", leaseDuration.toMillis());
    List<LeasedOutboxMessage> messages = jdbc.query(CLAIM_SQL, parameters, this::map);
    messages.sort(DELIVERY_ORDER);
    return List.copyOf(messages);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public boolean markPublished(OutboxLease lease) {
    return jdbc.update(PUBLISH_SQL, leaseParameters(lease)) == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public boolean releaseForRetry(OutboxLease lease, Duration delay, String error) {
    if (delay.isNegative() || error == null || error.length() > MAX_ERROR_LENGTH) {
      throw new IllegalArgumentException("invalid retry completion");
    }
    Map<String, Object> parameters = new java.util.HashMap<>(leaseParameters(lease));
    parameters.put("delayMillis", delay.toMillis());
    parameters.put("error", error);
    return jdbc.update(RETRY_SQL, parameters) == 1;
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
  public OutboxBacklogSnapshot snapshot() {
    return jdbc.queryForObject(
        SNAPSHOT_SQL,
        Map.of(),
        (resultSet, rowNumber) ->
            new OutboxBacklogSnapshot(
                resultSet.getLong("pending_count"),
                resultSet.getLong("leased_count"),
                resultSet.getDouble("oldest_pending_seconds")));
  }

  private Map<String, Object> leaseParameters(OutboxLease lease) {
    return Map.of("eventId", lease.eventId(), "owner", lease.owner(), "version", lease.version());
  }

  private LeasedOutboxMessage map(ResultSet resultSet, int rowNumber) throws SQLException {
    OutboxLease lease =
        new OutboxLease(
            resultSet.getObject("event_id", java.util.UUID.class),
            resultSet.getString("lease_owner"),
            resultSet.getLong("lease_version"),
            resultSet.getTimestamp("lease_until").toInstant());
    return new LeasedOutboxMessage(
        lease,
        resultSet.getString("topic"),
        resultSet.getString("partition_key"),
        resultSet.getString("schema_name"),
        resultSet.getBytes("payload"),
        resultSet.getLong("stream_sequence"),
        resultSet.getBoolean("lease_takeover"),
        resultSet.getInt("attempt_count"),
        resultSet.getTimestamp("created_at").toInstant());
  }
}
