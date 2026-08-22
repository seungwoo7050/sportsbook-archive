package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.api.AuditLogView;
import com.sportsbook.admin.api.OffsetPage;
import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AuditLogViewTest {

  @Test
  void representsAnUnfinishedActionWithoutInventingTerminalFields() {
    Instant started = Instant.parse("2026-08-22T01:02:03Z");
    AuditLogEntity entity =
        new AuditLogEntity(
            UUID.fromString("018f0000-0000-7000-8000-000000000096"),
            "operator-1",
            AdminRole.READONLY,
            "AUDIT_SEARCH",
            null,
            AuditOutcome.STARTED,
            null,
            null,
            "trace-1",
            started,
            null);

    AuditLogView view = AuditLogView.from(entity);

    assertThat(view.actorRole()).isEqualTo("READONLY");
    assertThat(view.outcome()).isEqualTo("STARTED");
    assertThat(view.httpStatus()).isNull();
    assertThat(view.startedAt()).isEqualTo(started);
    assertThat(view.completedAt()).isNull();
  }

  @Test
  void copiesPageMetadataAndItems() {
    AuditLogView item = AuditLogView.from(terminal());
    var source = new PageImpl<>(List.of(terminal()), PageRequest.of(2, 1), 7);

    OffsetPage<AuditLogView> page = OffsetPage.from(source, List.of(item));

    assertThat(page.items()).containsExactly(item);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(1);
    assertThat(page.totalElements()).isEqualTo(7);
    assertThat(page.totalPages()).isEqualTo(7);
  }

  private static AuditLogEntity terminal() {
    Instant started = Instant.parse("2026-08-22T01:02:03Z");
    return new AuditLogEntity(
        UUID.fromString("018f0000-0000-7000-8000-000000000097"),
        "operator-1",
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.SUCCESS,
        202,
        "operator request",
        "trace-1",
        started,
        started.plusSeconds(1));
  }
}
