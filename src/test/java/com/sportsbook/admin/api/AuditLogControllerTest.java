package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.audit.AuditLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

class AuditLogControllerTest {

  @Test
  void boundsPagingAndTreatsBlankActorAsAbsent() {
    AuditLogRepository repository = mock(AuditLogRepository.class);
    Instant from = Instant.parse("2026-08-22T00:00:00Z");
    Instant to = Instant.parse("2026-08-23T00:00:00Z");
    when(repository.search(any(), any(), isNull(), any())).thenReturn(Page.empty());
    var controller = new AuditLogController(repository);

    OffsetPage<AuditLogView> result = controller.search(from, to, "  ", -4, 500);

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).search(org.mockito.ArgumentMatchers.eq(from),
        org.mockito.ArgumentMatchers.eq(to), isNull(), page.capture());
    assertThat(page.getValue().getPageNumber()).isZero();
    assertThat(page.getValue().getPageSize()).isEqualTo(200);
    assertThat(page.getValue().getSort().getOrderFor("startedAt").isDescending()).isTrue();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void rejectsAnEmptyTimeWindowBeforeQuerying() {
    AuditLogRepository repository = mock(AuditLogRepository.class);
    var controller = new AuditLogController(repository);
    Instant boundary = Instant.parse("2026-08-23T00:00:00Z");

    assertThatThrownBy(() -> controller.search(boundary, boundary, null, 0, 20))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    verify(repository, never()).search(any(), any(), any(), any());
  }

  @Test
  void permitsEveryOperatorRole() throws NoSuchMethodException {
    PreAuthorize guard =
        AuditLogController.class
            .getMethod(
                "search", Instant.class, Instant.class, String.class, int.class, int.class)
            .getAnnotation(PreAuthorize.class);

    assertThat(guard.value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER','CS','READONLY')");
  }
}
