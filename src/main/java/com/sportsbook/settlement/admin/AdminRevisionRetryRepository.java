package com.sportsbook.settlement.admin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminRevisionRetryRepository {

  private final JdbcTemplate jdbc;

  public AdminRevisionRetryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Queued> queue(UUID revisionId) {
    return jdbc
        .query(
            """
            update settlement_revision set
                state = case when wallet_status = 'BLOCKED' then 'BLOCKED' else 'PENDING' end,
                lease_token = null, lease_until = null, attempt_count = 0,
                next_retry_at = case when wallet_status = 'BLOCKED'
                    then greatest(current_timestamp, wallet_next_attempt_at)
                    else current_timestamp end,
                last_error_code = null,
                updated_at = current_timestamp
            where revision_id = ? and lease_token is null and (
                state = 'EXHAUSTED'
                or (state = 'BLOCKED' and next_retry_at is null
                    and wallet_status = 'BLOCKED' and last_error_code is not null))
            returning state, next_retry_at, wallet_status = 'BLOCKED' as blocked_proof
            """,
            (result, rowNumber) ->
                new Queued(
                    result.getString("state"),
                    result.getBoolean("blocked_proof"),
                    result.getTimestamp("next_retry_at").toInstant()),
            revisionId)
        .stream()
        .findFirst();
  }

  public record Queued(String state, boolean blockedProof, Instant nextRetryAt) {}
}
