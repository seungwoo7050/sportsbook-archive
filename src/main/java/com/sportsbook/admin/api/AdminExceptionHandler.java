package com.sportsbook.admin.api;

import com.sportsbook.admin.client.DownstreamStatusException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class AdminExceptionHandler {

  @ExceptionHandler(DownstreamStatusException.class)
  ResponseEntity<byte[]> relayDownstream(DownstreamStatusException failure) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(
        failure.contentType() == null
            ? MediaType.APPLICATION_PROBLEM_JSON
            : failure.contentType());
    headers.setCacheControl("no-store");
    return new ResponseEntity<>(failure.body(), headers, failure.status());
  }
}
