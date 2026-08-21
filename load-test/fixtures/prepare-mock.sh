#!/usr/bin/env bash

wait_for_service() {
  local attempt
  for ((attempt = 0; attempt < 120; attempt++)); do
    if curl --fail --silent "${BASE_URL}/actuator/health/readiness" >/dev/null; then
      return
    fi
    if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
      echo "Service exited while starting" >&2
      return 1
    fi
    sleep 1
  done
  echo "Service readiness timed out" >&2
  return 1
}

wait_for_events() {
  local attempt response
  for ((attempt = 0; attempt < 30; attempt++)); do
    response=$(curl --fail --silent "${BASE_URL}/api/v1/events?size=20" || true)
    if jq -e '.items | type == "array" and length > 0' <<<"${response}" >/dev/null; then
      return
    fi
    sleep 1
  done
  echo "Mock event fixture did not appear" >&2
  return 1
}

discover_odds_fixture() {
  local attempt key
  for ((attempt = 0; attempt < 120; attempt++)); do
    key=$(compose exec -T redis redis-cli --raw --scan --pattern 'odds:*' \
      | sed -n '1p' | tr -d '\r')
    if [[ -n "${key}" ]]; then
      IFS=: read -r _ EVENT_ID MARKET_ID SELECTION_ID <<<"${key}"
      EXPECTED_ODDS=$(compose exec -T redis redis-cli --raw GET "${key}" | tr -d '\r')
      export EVENT_ID MARKET_ID SELECTION_ID EXPECTED_ODDS
      curl --fail --silent \
        "${BASE_URL}/api/v1/odds/${EVENT_ID}/${MARKET_ID}/${SELECTION_ID}" >/dev/null
      return
    fi
    sleep 1
  done
  echo "Mock odds fixture did not appear" >&2
  return 1
}

verify_frozen_odds() {
  curl --fail --silent \
    "${BASE_URL}/api/v1/odds/${EVENT_ID}/${MARKET_ID}/${SELECTION_ID}" \
    | jq -e --arg expected "${EXPECTED_ODDS}" '.odds == ($expected | tonumber)' >/dev/null
}
