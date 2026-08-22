package com.sportsbook.settlement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletProblemDetail(
    URI type, String title, int status, String detail, URI instance, String errorCode) {}
