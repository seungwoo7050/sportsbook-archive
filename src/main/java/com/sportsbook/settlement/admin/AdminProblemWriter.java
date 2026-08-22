package com.sportsbook.settlement.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public final class AdminProblemWriter {

  private final ObjectMapper mapper;

  public AdminProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String safeDetail)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, safeDetail);
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
