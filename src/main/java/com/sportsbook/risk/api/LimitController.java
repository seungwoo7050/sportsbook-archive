package com.sportsbook.risk.api;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import com.sportsbook.risk.limit.LimitOverrideStore;
import com.sportsbook.risk.limit.LimitResolver;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-owned effective-limit and override operations. */
@RestController
@RequestMapping("/internal/v1/risk/limits")
public class LimitController {
  private final LimitOverrideStore overrides;
  private final LimitResolver resolver;

  public LimitController(LimitOverrideStore overrides, LimitResolver resolver) {
    this.overrides = overrides;
    this.resolver = resolver;
  }

  @GetMapping("/{userId}")
  public UserLimitsResponse get(@PathVariable UUID userId) {
    UserId typedUserId = UserId.of(userId);
    var entries = new ArrayList<UserLimitsResponse.Entry>();
    for (LimitType type : LimitType.values()) {
      if (type.currencyScoped()) {
        for (Currency currency : Currency.values()) {
          entries.add(entry(typedUserId, new LimitOverrideTarget(type, currency)));
        }
      } else {
        entries.add(entry(typedUserId, new LimitOverrideTarget(type, null)));
      }
    }
    return new UserLimitsResponse(typedUserId, entries);
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<Void> update(
      @PathVariable UUID userId, @Valid @RequestBody LimitUpdateRequest request) {
    overrides.set(UserId.of(userId), request.target().field(), request.value());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{userId}/{type}")
  public ResponseEntity<Void> clear(
      @PathVariable UUID userId,
      @PathVariable LimitType type,
      @RequestParam(required = false) Currency currency) {
    LimitOverrideTarget target;
    try {
      target = new LimitOverrideTarget(type, currency);
    } catch (IllegalArgumentException exception) {
      throw RiskApiException.validation(exception.getMessage());
    }
    overrides.clear(UserId.of(userId), target.field());
    return ResponseEntity.noContent().build();
  }

  private UserLimitsResponse.Entry entry(UserId userId, LimitOverrideTarget target) {
    LimitOverrideField field = target.field();
    OptionalLong override = overrides.find(userId, field);
    long value =
        override.orElseGet(() -> resolver.resolve(userId, target.type(), target.currency()));
    var source =
        override.isPresent()
            ? UserLimitsResponse.Source.OVERRIDE
            : UserLimitsResponse.Source.POLICY;
    return new UserLimitsResponse.Entry(target.type(), target.currency(), value, source);
  }
}
