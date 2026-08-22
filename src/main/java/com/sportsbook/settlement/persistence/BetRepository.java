package com.sportsbook.settlement.persistence;

import com.sportsbook.settlement.domain.Bet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface BetRepository extends JpaRepository<Bet, UUID> {

  @Query("select b from Bet b left join fetch b.selections where b.betId = :id")
  Optional<Bet> findWithSelectionsById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Bet b where b.betId = :id")
  Optional<Bet> findForUpdateById(@Param("id") UUID id);

  @Query(
      "select distinct b.betId from Bet b join b.selections s "
          + "where s.eventId = :eventId and b.status = "
          + "com.sportsbook.settlement.domain.SettlementStatus.PENDING order by b.betId")
  List<UUID> findPendingIdsByEvent(@Param("eventId") UUID eventId);

  @Query(
      value =
          "select bet_id from bet where status = 'PENDING' and bet_id in (:ids) "
              + "order by bet_id for update",
      nativeQuery = true)
  @QueryHints({
    @QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"),
    @QueryHint(name = "jakarta.persistence.query.timeout", value = "2000")
  })
  List<UUID> lockPendingIds(@Param("ids") List<UUID> ids);

  @Query(
      value = "select bet_id from bet where bet_id in (:ids) order by bet_id for update",
      nativeQuery = true)
  List<UUID> lockIds(@Param("ids") List<UUID> ids);

  @Query(
      "select distinct b from Bet b left join fetch b.selections "
          + "where b.betId in :ids order by b.betId")
  List<Bet> findAllWithSelectionsByIdIn(@Param("ids") List<UUID> ids);
}
