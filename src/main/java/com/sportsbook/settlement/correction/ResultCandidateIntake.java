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
    var accepted = store.findAcceptedCandidate(result.eventId());
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
            accepted.map(ResultCandidateStore.AcceptedCandidate::candidateId).orElse(null));
    ResultCandidateStore.RecordOutcome recorded = store.record(candidate);
    if (recorded.kind() != ResultCandidateStore.RecordKind.CREATED) {
      return recorded.kind() == ResultCandidateStore.RecordKind.EXACT_REPLAY
          ? IntakeResult.EXACT_REPLAY
          : IntakeResult.NO_CHANGE;
    }
    if (accepted.isEmpty()) {
      return store.acceptFirst(candidate.candidateId(), result.receivedAt())
          ? IntakeResult.FIRST_ACCEPTED
          : IntakeResult.CORRECTION_PENDING;
    }
    ResultCandidateStore.AcceptedCandidate current = accepted.orElseThrow();
    if (result.receivedAt().isAfter(current.receivedAt().plus(runtime.correctionWindow()))) {
      return IntakeResult.LATE_HELD;
    }
    return store.replaceAccepted(
            candidate.candidateId(), current.candidateId(), result.receivedAt())
        ? IntakeResult.AUTO_CORRECTION_ACCEPTED
        : IntakeResult.CORRECTION_PENDING;
  }

  public enum IntakeResult {
    EXACT_REPLAY,
    NO_CHANGE,
    FIRST_ACCEPTED,
    AUTO_CORRECTION_ACCEPTED,
    LATE_HELD,
    CORRECTION_PENDING
  }
}
