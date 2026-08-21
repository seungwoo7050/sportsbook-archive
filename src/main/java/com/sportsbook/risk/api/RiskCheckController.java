package com.sportsbook.risk.api;

import com.sportsbook.risk.service.RiskCheckCommand;
import com.sportsbook.risk.service.RiskCheckOutcome;
import com.sportsbook.risk.service.RiskCheckService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-owned diagnostic endpoint; reservation admission remains authoritative. */
@RestController
@RequestMapping("/internal/v1/risk")
public class RiskCheckController {
  private final Function<RiskCheckCommand, RiskCheckOutcome> check;
  private final Clock clock;

  @Autowired
  public RiskCheckController(RiskCheckService service, Clock clock) {
    this(
        (Function<RiskCheckCommand, RiskCheckOutcome>)
            Objects.requireNonNull(service, "service")::check,
        clock);
  }

  RiskCheckController(Function<RiskCheckCommand, RiskCheckOutcome> check, Clock clock) {
    this.check = Objects.requireNonNull(check, "check");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @PostMapping("/check")
  public RiskCheckResponse check(@Valid @RequestBody RiskCheckRequest request) {
    return RiskCheckResponse.from(check.apply(request.toCommand(clock.instant())));
  }
}
