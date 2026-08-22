package com.sportsbook.settlement.correction;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RevisionRecoveryRepository {

  private final JdbcTemplate jdbc;

  public RevisionRecoveryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public List<Claim> claimDue(Duration leaseDuration, int limit) {
    long leaseMillis = leaseDuration == null ? 0 : leaseDuration.toMillis();
    if (leaseMillis < 1 || limit < 1 || limit > 1000) {
      throw new IllegalArgumentException("Invalid revision recovery bounds");
    }
    jdbc.update(
        """
        update settlement_revision set
            state = case when wallet_status = 'BLOCKED' then 'BLOCKED' else 'EXHAUSTED' end,
            lease_token = null, lease_until = null, next_retry_at = null,
            last_error_code = 'WALLET_RETRY_EXHAUSTED', updated_at = current_timestamp
        where state in ('PENDING', 'BLOCKED') and attempt_count >= 12
            and lease_token is not null and lease_until <= current_timestamp
        """);
    List<Candidate> due =
        jdbc.query(
            """
            select revision_id, wallet_status from settlement_revision
            where attempt_count < 12 and state in ('PENDING', 'BLOCKED') and (
                (lease_token is null and (
                    (state = 'PENDING' and next_retry_at <= current_timestamp)
                    or (state = 'BLOCKED' and next_retry_at <= current_timestamp
                        and wallet_status = 'BLOCKED'
                        and wallet_next_attempt_at <= current_timestamp)))
                or (lease_token is not null and lease_until <= current_timestamp))
            order by coalesce(next_retry_at, lease_until), revision_id
            limit ? for update skip locked
            """,
            (result, rowNumber) ->
                new Candidate(
                    result.getObject("revision_id", UUID.class),
                    "BLOCKED".equals(result.getString("wallet_status"))),
            limit);
    List<Claim> claimed = new ArrayList<>(due.size());
    for (Candidate candidate : due) {
      UUID token = UUID.randomUUID();
      Timestamp until =
          jdbc
              .query(
                  """
                  update settlement_revision set state = 'PENDING', lease_token = ?,
                      lease_until = current_timestamp + (? * interval '1 millisecond'),
                      attempt_count = attempt_count + 1, last_error_code = null,
                      next_retry_at = null,
                      updated_at = current_timestamp
                  where revision_id = ? and attempt_count < 12
                      and state in ('PENDING', 'BLOCKED')
                  returning lease_until
                  """,
                  (result, rowNumber) -> result.getTimestamp("lease_until"),
                  token,
                  leaseMillis,
                  candidate.revisionId())
              .stream()
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("Revision claim lost its locked row"));
      claimed.add(
          new Claim(
              candidate.revisionId(),
              new RevisionLease(token, until.toInstant()),
              candidate.blockedProof()));
    }
    return List.copyOf(claimed);
  }

  record Candidate(UUID revisionId, boolean blockedProof) {}

  public record Claim(UUID revisionId, RevisionLease lease, boolean blockedProof) {}
}
