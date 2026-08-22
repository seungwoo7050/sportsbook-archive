package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.config.SettlementRuntimeProperties;
import com.sportsbook.settlement.result.MatchResultRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultCandidateIntake {

  private final ResultCandidateStore store;
  private final ResultCandidateFingerprinter fingerprints;
  private final SettlementRuntimeProperties runtime;

  public ResultCandidateIntake(ResultCandidateStore store) {
    this(store, new SettlementRuntimeProperties(null, null, null, 0));
  }

  @Autowired
  public ResultCandidateIntake(ResultCandidateStore store, SettlementRuntimeProperties runtime) {
    this.store = store;
    this.fingerprints = new ResultCandidateFingerprinter();
    this.runtime = runtime;
  }

  @Transactional
  public IntakeResult ingest(MatchResultRecord result) {
    var acceptedAtRecord = store.findAcceptedCandidate(result.eventId());
    String fingerprint =
        fingerprints.fingerprint(result.eventId(), result.mode(), result.outcomes());
    ResultCandidate candidate =
        ResultCandidate.pending(
            result.eventId(),
            fingerprint,
            result.mode(),
            result.outcomes(),
            result.settledAt(),
            result.receivedAt(),
            acceptedAtRecord.map(ResultCandidateStore.AcceptedCandidate::candidateId).orElse(null));
    ResultCandidateStore.RecordOutcome recorded = store.record(candidate);
    if (recorded.state() == ResultCandidateState.ACCEPTED) {
      return IntakeResult.ACCEPTED_REPLAY;
    }
    if (recorded.state() != ResultCandidateState.PENDING) {
      return IntakeResult.NO_CHANGE;
    }
    if (store.holdWhileFuture(recorded.candidateId())) {
      return IntakeResult.FUTURE_HELD;
    }
    var accepted = store.findAcceptedCandidate(result.eventId());
    var candidateReceivedAt =
        recorded.receivedAt() == null ? result.receivedAt() : recorded.receivedAt();
    if (accepted.isEmpty()) {
      if (store.acceptFirst(recorded.candidateId(), result.receivedAt())) {
        return IntakeResult.FIRST_ACCEPTED;
      }
      return store.supersedeStale(recorded.candidateId(), result.receivedAt())
          ? IntakeResult.CORRECTION_SUPERSEDED
          : IntakeResult.CORRECTION_PENDING;
    }
    ResultCandidateStore.AcceptedCandidate current = accepted.orElseThrow();
    if (candidateReceivedAt.isAfter(current.receivedAt().plus(runtime.correctionWindow()))) {
      return IntakeResult.LATE_HELD;
    }
    if (store.replaceAccepted(recorded.candidateId(), current.candidateId(), result.receivedAt())) {
      return IntakeResult.AUTO_CORRECTION_ACCEPTED;
    }
    return store.supersedeStale(recorded.candidateId(), result.receivedAt())
        ? IntakeResult.CORRECTION_SUPERSEDED
        : IntakeResult.CORRECTION_PENDING;
  }

  public enum IntakeResult {
    EXACT_REPLAY,
    ACCEPTED_REPLAY,
    NO_CHANGE,
    FIRST_ACCEPTED,
    AUTO_CORRECTION_ACCEPTED,
    FUTURE_HELD,
    LATE_HELD,
    CORRECTION_SUPERSEDED,
    CORRECTION_PENDING
  }
}
