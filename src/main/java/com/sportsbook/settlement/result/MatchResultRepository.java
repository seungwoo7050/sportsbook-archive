package com.sportsbook.settlement.result;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResultRecord, UUID> {}
