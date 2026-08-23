package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminRequestHeadersTest {

  @Test
  void preservesOneGeneralKeyWithoutNormalization() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, " retry key 01 ");

    assertThat(AdminRequestHeaders.requireIdempotencyKey(request).value())
        .isEqualTo(" retry key 01 ");
  }

  @Test
  void parsesOneUuidKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, "018f0000-0000-7000-8000-000000000111");

    assertThat(AdminRequestHeaders.requireUuidIdempotencyKey(request))
        .isEqualTo(UUID.fromString("018f0000-0000-7000-8000-000000000111"));
  }

  @Test
  void rejectsMissingDuplicateAndInvalidKeys() {
    MockHttpServletRequest missing = new MockHttpServletRequest();
    assertThatThrownBy(() -> AdminRequestHeaders.requireIdempotencyKey(missing))
        .isInstanceOf(AdminRequestException.class)
        .hasMessageContaining("Exactly one");

    MockHttpServletRequest duplicate = new MockHttpServletRequest();
    duplicate.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, "first");
    duplicate.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, "second");
    assertThatThrownBy(() -> AdminRequestHeaders.requireIdempotencyKey(duplicate))
        .isInstanceOf(AdminRequestException.class)
        .hasMessageContaining("Exactly one");

    MockHttpServletRequest invalidUuid = new MockHttpServletRequest();
    invalidUuid.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, "not-a-uuid");
    assertThatThrownBy(() -> AdminRequestHeaders.requireUuidIdempotencyKey(invalidUuid))
        .isInstanceOf(AdminRequestException.class)
        .hasMessageContaining("UUID");
  }
}
