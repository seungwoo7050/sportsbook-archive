package com.sportsbook.betting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.placement.PlacementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;

class PlacementRequestRepositoryTest {

  @Test
  void ownsPlacementKeysAsStrings() {
    ResolvableType repository =
        ResolvableType.forClass(PlacementRequestRepository.class).as(JpaRepository.class);

    assertThat(repository.getGeneric(0).resolve()).isEqualTo(PlacementRequest.class);
    assertThat(repository.getGeneric(1).resolve()).isEqualTo(String.class);
  }
}
