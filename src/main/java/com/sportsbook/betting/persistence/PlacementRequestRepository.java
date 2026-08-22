package com.sportsbook.betting.persistence;

import com.sportsbook.betting.placement.PlacementRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementRequestRepository extends JpaRepository<PlacementRequest, String> {}
