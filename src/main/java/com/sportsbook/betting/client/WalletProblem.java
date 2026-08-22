package com.sportsbook.betting.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletProblem(String errorCode, String detail) {}
