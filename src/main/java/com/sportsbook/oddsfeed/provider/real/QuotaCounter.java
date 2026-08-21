package com.sportsbook.oddsfeed.provider.real;

public interface QuotaCounter {

  long increment();

  long current();
}
