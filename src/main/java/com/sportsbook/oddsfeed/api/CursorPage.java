package com.sportsbook.oddsfeed.api;

import java.util.List;

/** A page with an optional cursor for the following page. */
public record CursorPage<T>(List<T> items, String nextCursor) {}
