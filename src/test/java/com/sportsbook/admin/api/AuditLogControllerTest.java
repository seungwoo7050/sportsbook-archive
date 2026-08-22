package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.audit.AuditLogEntity;
import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.audit.AuditOutcome;
import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
    verify(repository)
        .search(
            org.mockito.ArgumentMatchers.eq(from),
            org.mockito.ArgumentMatchers.eq(to),
            isNull(),
            page.capture());
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
            .getMethod("search", Instant.class, Instant.class, String.class, int.class, int.class)
            .getAnnotation(PreAuthorize.class);

    assertThat(guard.value()).isEqualTo("hasAnyRole('ADMIN','TRADER','CS','READONLY')");
  }

  @Test
  void returnsTheExactActionRequested() {
    AuditLogRepository repository = mock(AuditLogRepository.class);
    AuditLogEntity entity = mock(AuditLogEntity.class);
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000098");
    when(entity.getActionId()).thenReturn(actionId);
    when(entity.getActorId()).thenReturn("operator-1");
    when(entity.getActorRole()).thenReturn(AdminRole.CS);
    when(entity.getAction()).thenReturn("WALLET_REFUND");
    when(entity.getOutcome()).thenReturn(AuditOutcome.FAILED);
    when(entity.getHttpStatus()).thenReturn(409);
    when(repository.findById(actionId)).thenReturn(Optional.of(entity));

    AuditLogView view = new AuditLogController(repository).findByActionId(actionId);

    assertThat(view.actionId()).isEqualTo(actionId);
    assertThat(view.actorRole()).isEqualTo("CS");
    assertThat(view.outcome()).isEqualTo("FAILED");
    assertThat(view.httpStatus()).isEqualTo(409);
  }

  @Test
  void returnsNotFoundForAnUnknownAction() {
    AuditLogRepository repository = mock(AuditLogRepository.class);
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000099");
    when(repository.findById(actionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new AuditLogController(repository).findByActionId(actionId))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
