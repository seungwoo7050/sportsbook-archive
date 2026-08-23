package com.sportsbook.admin.client;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

public final class DownstreamFailureMapper {

  public <T> T execute(Supplier<T> request) {
    try {
      return request.get();
    } catch (HttpClientErrorException rejection) {
      HttpHeaders headers = rejection.getResponseHeaders();
      MediaType contentType = headers == null ? null : headers.getContentType();
      throw new DownstreamStatusException(
          rejection.getStatusCode(), contentType, rejection.getResponseBodyAsByteArray());
    } catch (HttpServerErrorException serverError) {
      throw new DownstreamUnavailableException(
          DownstreamUnavailableException.Reason.SERVER_ERROR,
          serverError.getStatusCode(),
          serverError);
    } catch (ResourceAccessException transportFailure) {
      DownstreamUnavailableException.Reason reason =
          hasCause(transportFailure, SocketTimeoutException.class)
              ? DownstreamUnavailableException.Reason.TIMEOUT
              : DownstreamUnavailableException.Reason.TRANSPORT;
      throw new DownstreamUnavailableException(reason, null, transportFailure);
    } catch (RestClientException clientFailure) {
      if (hasCause(clientFailure, HttpMessageConversionException.class)) {
        throw new DownstreamContractException("deserializable response body", clientFailure);
      }
      throw new DownstreamUnavailableException(
          DownstreamUnavailableException.Reason.TRANSPORT, null, clientFailure);
    }
  }

  public void execute(Runnable request) {
    execute(
        () -> {
          request.run();
          return null;
        });
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }
}
