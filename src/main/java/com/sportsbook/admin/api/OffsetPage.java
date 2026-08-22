package com.sportsbook.admin.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record OffsetPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <T> OffsetPage<T> from(Page<?> source, List<T> items) {
    return new OffsetPage<>(
        List.copyOf(items),
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }
}
