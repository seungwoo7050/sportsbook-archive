package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AuditLogEntity;
import com.sportsbook.admin.audit.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/v1/audit-logs")
public class AuditLogController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final AuditLogRepository repository;

  public AuditLogController(AuditLogRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','TRADER','CS','READONLY')")
  public OffsetPage<AuditLogView> search(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Instant lower = from == null ? Instant.EPOCH : from;
    Instant upper = to == null ? Instant.now() : to;
    if (!lower.isBefore(upper)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
    }
    int normalizedPage = Math.max(0, page);
    int normalizedSize = Math.min(size < 1 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
    Sort newestFirst =
        Sort.by(Sort.Direction.DESC, "startedAt")
            .and(Sort.by(Sort.Direction.DESC, "actionId"));
    Page<AuditLogEntity> result =
        repository.search(
            lower,
            upper,
            normalizeActor(actor),
            PageRequest.of(normalizedPage, normalizedSize, newestFirst));
    List<AuditLogView> items = result.stream().map(AuditLogView::from).toList();
    return OffsetPage.from(result, items);
  }

  private static String normalizeActor(String actor) {
    return actor == null || actor.isBlank() ? null : actor.trim();
  }
}
